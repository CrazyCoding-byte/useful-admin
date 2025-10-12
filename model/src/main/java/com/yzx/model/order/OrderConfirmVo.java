package com.yzx.model.order;

import com.yzx.model.cart.vo.CartItemVo;
import com.yzx.model.coupon.CartInfoVo;
import com.yzx.model.coupon.CouponInfo;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;



public class OrderConfirmVo {

    @Getter @Setter
    /** 会员收获地址列表 **/
    List<MemberAddressVo> memberAddressVos;

    @Getter @Setter
    /** 所有选中的购物项 **/
    List<OrderItemVo> items;

    /** 发票记录 **/
    @Getter @Setter
    /** 优惠券（会员积分） **/
    private Integer integration;

    /** 防止重复提交的令牌 **/
    @Getter @Setter
    private String orderToken;

    @Getter @Setter
    Map<Long,Boolean> stocks;

    private List<CartInfoVo> carInfoVoList;

    private List<CouponInfo> couponInfoList;

    private BigDecimal activityReduceAmount;

    private BigDecimal couponReduceAmount;

    private BigDecimal originalTotalAmount;

    private BigDecimal totalAmount;

    public List<MemberAddressVo> getMemberAddressVos() {
        return memberAddressVos;
    }

    public void setMemberAddressVos(List<MemberAddressVo> memberAddressVos) {
        this.memberAddressVos = memberAddressVos;
    }

    public List<OrderItemVo> getItems() {
        return items;
    }

    public void setItems(List<OrderItemVo> items) {
        this.items = items;
    }

    public Integer getIntegration() {
        return integration;
    }

    public void setIntegration(Integer integration) {
        this.integration = integration;
    }

    public String getOrderToken() {
        return orderToken;
    }

    public void setOrderToken(String orderToken) {
        this.orderToken = orderToken;
    }

    public Map<Long, Boolean> getStocks() {
        return stocks;
    }

    public void setStocks(Map<Long, Boolean> stocks) {
        this.stocks = stocks;
    }

    public List<CartInfoVo> getCarInfoVoList() {
        return carInfoVoList;
    }

    public void setCarInfoVoList(List<CartInfoVo> carInfoVoList) {
        this.carInfoVoList = carInfoVoList;
    }

    public List<CouponInfo> getCouponInfoList() {
        return couponInfoList;
    }

    public void setCouponInfoList(List<CouponInfo> couponInfoList) {
        this.couponInfoList = couponInfoList;
    }

    public BigDecimal getActivityReduceAmount() {
        return activityReduceAmount;
    }

    public void setActivityReduceAmount(BigDecimal activityReduceAmount) {
        this.activityReduceAmount = activityReduceAmount;
    }

    public BigDecimal getCouponReduceAmount() {
        return couponReduceAmount;
    }

    public void setCouponReduceAmount(BigDecimal couponReduceAmount) {
        this.couponReduceAmount = couponReduceAmount;
    }

    public BigDecimal getOriginalTotalAmount() {
        return originalTotalAmount;
    }

    public void setOriginalTotalAmount(BigDecimal originalTotalAmount) {
        this.originalTotalAmount = originalTotalAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public Integer getCount() {
        Integer count = 0;
        if (items != null && items.size() > 0) {
            for (OrderItemVo item : items) {
                count += item.getCount();
            }
        }
        return count;
    }


    /** 订单总额 **/
    //BigDecimal total;
    //计算订单总额
    public BigDecimal getTotal() {
        BigDecimal totalNum = BigDecimal.ZERO;
        if (items != null && items.size() > 0) {
            for (OrderItemVo item : items) {
                //计算当前商品的总价格
                BigDecimal itemPrice = item.getPrice().multiply(new BigDecimal(item.getCount().toString()));
                //再计算全部商品的总价格
                totalNum = totalNum.add(itemPrice);
            }
        }
        return totalNum;
    }


    /** 应付价格 **/
    //BigDecimal payPrice;
    public BigDecimal getPayPrice() {
        return getTotal();
    }
}
