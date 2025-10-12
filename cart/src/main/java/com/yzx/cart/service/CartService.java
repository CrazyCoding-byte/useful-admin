package com.yzx.cart.service;

import com.yzx.model.cart.vo.CartItemVo;
import com.yzx.model.cart.vo.CartVo;

import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * @className: CartService
 * @author: yzx
 * @date: 2025/8/29 11:32
 * @Version: 1.0
 * @description:
 */
public interface CartService {
    void addCart(Long skuId, Integer num) throws ExecutionException, InterruptedException;

    void checkCartItem(Long skuId, Integer check);

    void changeItemCount(Long skuId, Integer num);

    void deleteIdCartInfo(Integer skuId);

    CartVo getCart();

    List<CartItemVo> getUserCartItems();
}
