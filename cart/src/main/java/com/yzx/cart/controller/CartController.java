package com.yzx.cart.controller;

import com.yzx.cart.service.CartService;
import com.yzx.model.AjaxResult;
import com.yzx.model.cart.vo.CartItemVo;
import com.yzx.model.cart.vo.CartVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * @className: CartController
 * @author: yzx
 * @date: 2025/8/29 11:27
 * @Version: 1.0
 * @description:
 */
@RestController
@RequestMapping("cart")
@Slf4j
public class CartController {
    @Autowired
    private CartService cartService;

    /**
     * 获取当前用户的购物车商品项
     * @return
     */
    @GetMapping(value = "/currentUserCartItems")
    @ResponseBody
    public List<CartItemVo> getCurrentCartItems() {

        List<CartItemVo> cartItemVoList = cartService.getUserCartItems();

        return cartItemVoList;
    }

    /**
     *  获取用户购物车数据
     * @return
     */
    @GetMapping(value = "/getcart")
    public AjaxResult cartListPage() throws ExecutionException, InterruptedException {
        CartVo cartVo = cartService.getCart();

        return AjaxResult.success(cartVo);
    }

    /**
     * 添加商品到购物车
     * @param skuId
     * @param num
     */
    @GetMapping("/addCartItem")
    public void addCardItem(@RequestParam("skuId") Long skuId, @RequestParam("num") Integer num) throws ExecutionException, InterruptedException {
        log.info("addCartItem:skuId:{},num:{}", skuId, num);
        cartService.addCart(skuId, num);
    }

    /**
     * 检查购物车商品是否被选中
     */
    @GetMapping("/checkCartItem")
    public void checkCartItem(@RequestParam("skuId") Long skuId, @RequestParam("check") Integer check) {
        log.info("checkCartItem:skuId:{},check:{}", skuId, check);
        cartService.checkCartItem(skuId, check);
    }

    /**
     * 修改购物车商品数量
     * @param skuId
     * @param num
     * @return
     */
    @GetMapping(value = "/countItem")
    public void countItem(@RequestParam(value = "skuId") Long skuId,
                          @RequestParam(value = "num") Integer num) {

        cartService.changeItemCount(skuId, num);
    }

    /**
     * 删除商品信息
     * @param skuId
     * @return
     */
    @GetMapping(value = "/deleteItem")
    public void deleteItem(@RequestParam("skuId") Integer skuId) {

        cartService.deleteIdCartInfo(skuId);

    }

}
