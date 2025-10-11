package com.yzx.order.service.impl;

import com.alibaba.fastjson.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yzx.apiclient.api.CartFeignService;
import com.yzx.apiclient.api.MemberFeignService;
import com.yzx.apiclient.api.ProductFeignService;
import com.yzx.apiclient.api.WmsFeignService;
import com.yzx.common.config.JwtHelp;
import com.yzx.model.AjaxResult;
import com.yzx.model.exception.NoStockException;
import com.yzx.model.order.*;
import com.yzx.model.order.enums.OrderStatusEnum;
import com.yzx.model.order.to.OrderCreateTo;
import com.yzx.model.order.to.OrderTo;
import com.yzx.model.ucenter.BaseUser;
import com.yzx.order.mapper.OrderMapper;
import com.yzx.order.service.OrderItemService;
import com.yzx.order.service.OrderService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.yzx.model.cart.CartConst.CART_PREFIX;
import static com.yzx.model.order.constant.OrderConstant.USER_ORDER_TOKEN_PREFIX;

/**
 * @className: OrderServiceImpl
 * @author: yzx
 * @date: 2025/8/30 5:48
 * @Version: 1.0
 * @description:
 */
@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, OrderEntity> implements OrderService {

    private ThreadLocal<OrderSubmitVo> confirmVoThreadLocal = new ThreadLocal<>();
    @Autowired
    private JwtHelp jwtHelp;
    @Autowired
    private MemberFeignService memberFeignService;
    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;
    @Autowired
    private CartFeignService cartFeignService;
    @Autowired
    private WmsFeignService wmsFeignService;
    @Autowired
    private ProductFeignService productFeignService;
    @Autowired
    private OrderItemService orderItemService;
    @Autowired
    RabbitTemplate rabbitTemplate;
    @Autowired
    RedisTemplate redisTemplate;

    //订单确认
    @Override
    public OrderConfirmVo confirmOrder() throws ExecutionException, InterruptedException {
        //查询用户信息 查询收货地址信息 查询购物车信息 查询优惠券信息 查询积分信息
        //构建OrderConfirmVo
        OrderConfirmVo confirmVo = new OrderConfirmVo();
        BaseUser baseUser = jwtHelp.getCurrentUser().getBaseUser();
        if (Objects.isNull(baseUser)) return null;
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        CompletableFuture<Void> addressFuture = CompletableFuture.runAsync(() -> {
            //每一个线程都来共享之前的请求数据
            RequestContextHolder.setRequestAttributes(requestAttributes);
            //查询用户地址信息
            //1、根据用户的id远程查询所有的收获地址列表
            List<MemberAddressVo> address = memberFeignService.getAddress(baseUser.getUserId());
            confirmVo.setMemberAddressVos(address);
        }, threadPoolExecutor);

        //开启第二个异步任务
        CompletableFuture<Void> cartInfoFuture = CompletableFuture.runAsync(() -> {
            RequestContextHolder.setRequestAttributes(requestAttributes);
            //2、远程查询购物车所有选中的购物项
            List<OrderItemVo> currentCartItems = cartFeignService.getCurrentCartItems();
            confirmVo.setItems(currentCartItems);
        }, threadPoolExecutor).thenRunAsync(() -> {
            List<OrderItemVo> items = confirmVo.getItems();
            if (!CollectionUtils.isEmpty(items)) {
                List<Long> skuIds = items.stream().map(item -> item.getSkuId()).collect(Collectors.toList());
                if (!CollectionUtils.isEmpty(skuIds)) {
                    //远程查询商品库存信息
                    AjaxResult skuHasStock = wmsFeignService.getSkuHasStock(skuIds);
                    List<SkuStockVo> skuStockVos = skuHasStock.getData("data", new TypeReference<List<SkuStockVo>>() {
                    });
                    if (skuStockVos != null && skuStockVos.size() > 0) {
                        //将skuStockVos集合转换为map
                        Map<Long, Boolean> skuHasStockMap = skuStockVos.stream().collect(Collectors.toMap(SkuStockVo::getSkuId, SkuStockVo::getHasStock));
                        confirmVo.setStocks(skuHasStockMap);
                    }
                }
            }
        }, threadPoolExecutor);
        //TODO 3、自动算钱 这里算的钱没有扣优惠卷和提交订单的钱会起冲突这里优惠卷等后面完成
        //TODO 5、防重令牌(防止表单重复提交)
        //为用户设置一个token，三十分钟过期时间（存在redis）
        String token = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(USER_ORDER_TOKEN_PREFIX + baseUser.getUserId(), token, 30, TimeUnit.MINUTES);
        confirmVo.setOrderToken(token);
        CompletableFuture.allOf(addressFuture, cartInfoFuture).get();
        return confirmVo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SubmitOrderResponseVo submitOrder(OrderSubmitVo vo) {
        confirmVoThreadLocal.set(vo);
        SubmitOrderResponseVo responseVo = new SubmitOrderResponseVo();
        //去创建、下订单、验令牌、验价格、锁定库存...
        BaseUser baseUser = jwtHelp.getCurrentUser().getBaseUser();
        responseVo.setCode(0);
        //1、验证令牌是否合法【令牌的对比和删除必须保证原子性】
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
        String orderToken = vo.getOrderToken();
        //通过lure脚本原子验证令牌和删除令牌
        Long result = (Long) redisTemplate.execute(new DefaultRedisScript<Long>(script, Long.class),
                Arrays.asList(USER_ORDER_TOKEN_PREFIX + baseUser.getUserId()),
                orderToken);
        if (result == 0L) {
            //令牌验证失败
            responseVo.setCode(1);
            return responseVo;
        } else {
            //令牌验证成功
            OrderCreateTo order = createOrder();
            BigDecimal payAmount = order.getOrder().getPayAmount();
            BigDecimal payPrice = vo.getPayPrice();
            //前端和后端的金额对比 误差小于0.01
            if (Math.abs(payAmount.subtract(payPrice).doubleValue()) < 0.01) {
                //保存订单
                saveOrder(order);
                //库存锁定,只要有异常，回滚订单数据
                //订单号、所有订单项信息(skuId,skuNum,skuName)
                WareSkuLockVo lockVo = new WareSkuLockVo();
                lockVo.setOrderSn(order.getOrder().getOrderSn());
                //获取出要锁定的商品数据信息
                List<OrderItemVo> orderItemVos = order.getOrderItems().stream().map((item) -> {
                    OrderItemVo orderItemVo = new OrderItemVo();
                    orderItemVo.setSkuId(item.getSkuId());
                    orderItemVo.setCount(item.getSkuQuantity());
                    orderItemVo.setTitle(item.getSkuName());
                    return orderItemVo;
                }).collect(Collectors.toList());
                lockVo.setLocks(orderItemVos);
                //TODO 调用远程锁定库存的方法
                //出现的问题：扣减库存成功了，但是由于网络原因超时，出现异常，导致订单事务回滚，库存事务不回滚(解决方案：seata)
                //为了保证高并发，不推荐使用seata，因为是加锁，并行化，提升不了效率,可以发消息给库存服务
                AjaxResult r = wmsFeignService.orderLockStock(lockVo);
                if (r.getCode() == 0) {
                    //锁定成功
                    responseVo.setOrder(order.getOrder());
                    // int i = 10/0;
                    //TODO 订单创建成功，发送消息给MQ
                    rabbitTemplate.convertAndSend("order-event-exchange", "order.create.order", order.getOrder());
                    //删除购物车里的数据
                    redisTemplate.delete(CART_PREFIX + baseUser.getUserId());
                    return responseVo;
                } else {
                    //锁定失败
                    String msg = (String) r.get("msg");
                    throw new NoStockException(msg);
                    // responseVo.setCode(3);
                    // return responseVo;
                }
            } else {
                responseVo.setCode(2);
                return responseVo;
            }
        }
    }


    /**
     * 保存订单所有数据
     * @param orderCreateTo
     */
    private void saveOrder(OrderCreateTo orderCreateTo) {

        //获取订单信息
        OrderEntity order = orderCreateTo.getOrder();
        order.setModifyTime(new Date());
        order.setCreateTime(new Date());
        //保存订单
        this.baseMapper.insert(order);

        //获取订单项信息
        List<OrderItemEntity> orderItems = orderCreateTo.getOrderItems();
        //批量保存订单项数据
        orderItemService.saveBatch(orderItems);
    }

    private OrderCreateTo createOrder() {

        OrderCreateTo createTo = new OrderCreateTo();

        //1、生成订单号
        String orderSn = IdWorker.getTimeId();
        OrderEntity orderEntity = builderOrder(orderSn);
        //2、获取到所有的订单项
        List<OrderItemEntity> orderItemEntities = builderOrderItems(orderSn);

        //3、验价(计算价格、积分等信息)
        computePrice(orderEntity, orderItemEntities);

        createTo.setOrder(orderEntity);
        createTo.setOrderItems(orderItemEntities);

        return createTo;
    }

    /**
     * 计算价格的方法
     * @param orderEntity
     * @param orderItemEntities
     */
    private void computePrice(OrderEntity orderEntity, List<OrderItemEntity> orderItemEntities) {
        //总价
        BigDecimal total = new BigDecimal("0.0");
        //优惠价
        BigDecimal coupon = new BigDecimal("0.0");
        BigDecimal intergration = new BigDecimal("0.0");
        BigDecimal promotion = new BigDecimal("0.0");

        //积分、成长值
        Integer integrationTotal = 0;
        Integer growthTotal = 0;

        //订单总额，叠加每一个订单项的总额信息
        for (OrderItemEntity orderItem : orderItemEntities) {
            //优惠价格信息
            coupon = coupon.add(orderItem.getCouponAmount());
            promotion = promotion.add(orderItem.getPromotionAmount());
            intergration = intergration.add(orderItem.getIntegrationAmount());

            //总价
            total = total.add(orderItem.getRealAmount());

            //积分信息和成长值信息
            integrationTotal += orderItem.getGiftIntegration();
            growthTotal += orderItem.getGiftGrowth();

        }
        //1、订单价格相关的
        orderEntity.setTotalAmount(total);
        //设置应付总额(总额+运费)
        orderEntity.setPayAmount(total.add(orderEntity.getFreightAmount()));
        orderEntity.setCouponAmount(coupon);
        orderEntity.setPromotionAmount(promotion);
        orderEntity.setIntegrationAmount(intergration);

        //设置积分成长值信息
        orderEntity.setIntegration(integrationTotal);
        orderEntity.setGrowth(growthTotal);

        //设置删除状态(0-未删除，1-已删除)
        orderEntity.setDeleteStatus(0);

    }

    public List<OrderItemEntity> builderOrderItems(String orderSn) {
        List<OrderItemEntity> orderItemEntityList = new ArrayList<>();
        //最后确定每个购物项的价格
        List<OrderItemVo> currentCartItems = cartFeignService.getCurrentCartItems();
        if (currentCartItems != null && currentCartItems.size() > 0) {
            orderItemEntityList = currentCartItems.stream().map((items) -> {
                //构建订单项数据
                OrderItemEntity orderItemEntity = builderOrderItem(items);
                orderItemEntity.setOrderSn(orderSn);

                return orderItemEntity;
            }).collect(Collectors.toList());
        }
        return orderItemEntityList;
    }

    private OrderItemEntity builderOrderItem(OrderItemVo items) {
        OrderItemEntity orderItemEntity = new OrderItemEntity();
        Long skuId = items.getSkuId();
        //获取spu的信息
        AjaxResult spuInfo = productFeignService.getSpuInfoBySkuId(skuId);
        SpuInfoVo spuInfoData = spuInfo.getData("data", new TypeReference<SpuInfoVo>() {
        });
        orderItemEntity.setSpuId(spuInfoData.getId());
        orderItemEntity.setSpuName(spuInfoData.getSpuName());
        orderItemEntity.setSpuBrand(spuInfoData.getBrandName());
        orderItemEntity.setCategoryId(spuInfoData.getCatalogId());
        //2、商品的sku信息
        orderItemEntity.setSkuId(skuId);
        orderItemEntity.setSkuName(items.getTitle());
        orderItemEntity.setSkuPic(items.getImage());
        orderItemEntity.setSkuPrice(items.getPrice());
        orderItemEntity.setSkuQuantity(items.getCount());
        //使用StringUtils.collectionToDelimitedString将list集合转换为String
        String skuAttrValues = StringUtils.collectionToDelimitedString(items.getSkuAttrValues(), ";");
        orderItemEntity.setSkuAttrsVals(skuAttrValues);
        //3、商品的优惠信息

        //4、商品的积分信息
        orderItemEntity.setGiftGrowth(items.getPrice().multiply(new BigDecimal(items.getCount())).intValue());
        orderItemEntity.setGiftIntegration(items.getPrice().multiply(new BigDecimal(items.getCount())).intValue());

        //5、订单项的价格信息
        orderItemEntity.setPromotionAmount(BigDecimal.ZERO);
        orderItemEntity.setCouponAmount(BigDecimal.ZERO);
        orderItemEntity.setIntegrationAmount(BigDecimal.ZERO);
        //当前订单项的实际金额.总额 - 各种优惠价格
        //原来的价格
        BigDecimal origin = orderItemEntity.getSkuPrice().multiply(new BigDecimal(orderItemEntity.getSkuQuantity().toString()));
        //原价减去优惠价得到最终的价格
        BigDecimal subtract = origin.subtract(orderItemEntity.getCouponAmount())
                .subtract(orderItemEntity.getPromotionAmount())
                .subtract(orderItemEntity.getIntegrationAmount());
        orderItemEntity.setRealAmount(subtract);
        return orderItemEntity;
    }

    //构建订单信息
    private OrderEntity builderOrder(String orderSn) {
        BaseUser baseUser = jwtHelp.getCurrentUser().getBaseUser();
        if (Objects.isNull(baseUser)) return null;
        OrderEntity orderEntity = new OrderEntity();
        orderEntity.setMemberId(baseUser.getUserId());
        orderEntity.setOrderSn(orderSn);
        orderEntity.setMemberUsername(baseUser.getUserName());
        OrderSubmitVo orderSubmitVo = confirmVoThreadLocal.get();
        AjaxResult fareAddressVo = wmsFeignService.getFare(orderSubmitVo.getAddrId());
        //TODO 这里应该是远程调用第三方接口算出运费的价格
        FareVo fareResp = fareAddressVo.getData("data", new TypeReference<FareVo>() {
        });
        BigDecimal fare = fareResp.getFare();
        orderEntity.setFreightAmount(fare);
        MemberAddressVo address = fareResp.getAddress();
        //设置收货人信息
        orderEntity.setReceiverName(address.getName());
        orderEntity.setReceiverPhone(address.getPhone());
        orderEntity.setReceiverPostCode(address.getPostCode());
        orderEntity.setReceiverProvince(address.getProvince());
        orderEntity.setReceiverCity(address.getCity());
        orderEntity.setReceiverRegion(address.getRegion());
        orderEntity.setReceiverDetailAddress(address.getDetailAddress());
        //设置订单相关的状态信息
        orderEntity.setStatus(OrderStatusEnum.CREATE_NEW.getCode());
        orderEntity.setAutoConfirmDay(7);
        orderEntity.setConfirmStatus(0);
        return orderEntity;
    }

    @Override
    public void closeOrder(OrderEntity orderEntity) {
        //关闭订单之前先查询一下数据库，判断此订单状态是否已支付
        OrderEntity orderInfo = this.getOne(new QueryWrapper<OrderEntity>().
                eq("order_sn", orderEntity.getOrderSn()));
        if (orderInfo.getStatus().equals(OrderStatusEnum.CREATE_NEW.getCode())) {
            //代付款状态进行关单
            OrderEntity orderUpdate = new OrderEntity();
            orderUpdate.setId(orderInfo.getId());
            orderUpdate.setStatus(OrderStatusEnum.CANCLED.getCode());
            this.updateById(orderUpdate);
            // 发送消息给MQ
            OrderTo orderTo = new OrderTo();
            BeanUtils.copyProperties(orderInfo, orderTo);
            try {
                //TODO 确保每个消息发送成功，给每个消息做好日志记录，(给数据库保存每一个详细信息)保存每个消息的详细信息
                rabbitTemplate.convertAndSend("order-event-exchange", "order.release.other", orderTo);
            } catch (Exception e) {
                //TODO 定期扫描数据库，重新发送失败的消息
            }
        }
    }
}
