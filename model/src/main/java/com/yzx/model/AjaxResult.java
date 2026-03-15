package com.yzx.model;

/**
 * @program: xz-framework-parent-reversion
 * @description: 用于api接口响应
 * @author: wdw
 * @create: 2020-01-21 19:41
 **/


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;

import java.util.HashMap;
import java.util.Map;


/**
 * 操作消息提醒
 *
 */
public class AjaxResult extends HashMap<String, Object> {

    private static final long serialVersionUID = -8157613083634272196L;

    /**
     * 状态码
     */
    public static final String CODE_TAG = "code";

    /**
     * 请求成功的标记，true表示请求成功，false表示失败
     */
    public static final String SUCCESS_TAG = "flag";

    /**
     * 返回内容
     */
    public static final String MSG_TAG = "msg";

    /**
     * 数据对象
     */
    public static final String DATA_TAG = "data";

    /**
     * 响应的类型
     */
    public enum Type {
        /**
         * 成功200
         */
        SUCCESS(HttpStatus.SUCCESS),
        /**
         * 警告301
         */
        WARN(HttpStatus.MOVED_PERM),
        /**
         * 错误500
         */
        ERROR(HttpStatus.ERROR),

        /**
         * 参数列表错误400
         */
        BAD_REQUEST(HttpStatus.BAD_REQUEST);

        private final int value;

        Type(int value) {
            this.value = value;
        }

        public int value() {
            return this.value;
        }
    }

    public AjaxResult() {
        put(CODE_TAG, Type.SUCCESS.value());
        put(MSG_TAG,"success");
    }

    //————————————————响应成功—————————————————————

    public static AjaxResult success() {
        return new AjaxResult();
    }

    public static AjaxResult success(Object data) {
        AjaxResult result = new AjaxResult();
        result.put(DATA_TAG,data);
        return result;
    }

    public static AjaxResult success(Map<String, Object> map) {
        AjaxResult result = new AjaxResult();
        result.putAll(map);
        return result;
    }

    public static AjaxResult success(String msg) {
        AjaxResult result = new AjaxResult();
        result.put(MSG_TAG,msg);
        return result;
    }

    public static AjaxResult success(ResultCode resultCode) {
        AjaxResult result = new AjaxResult();
        result.put(CODE_TAG,resultCode.code());
        result.put(MSG_TAG,resultCode.msg());
        return result;
    }

    public static AjaxResult success(String msg, Object data) {
        AjaxResult result = new AjaxResult();
        result.put(MSG_TAG,msg);
        result.put(DATA_TAG,data);
        return result;
    }

    //————————————————响应失败—————————————————————

    public static AjaxResult error() {
        return error(Type.ERROR.value(),"未知异常，请联系管理员");
    }

    public static AjaxResult error(String msg) {
        return error(Type.ERROR.value(),msg);
    }

    public static AjaxResult error(int code,String msg) {
        AjaxResult result = new AjaxResult();
        result.put(CODE_TAG,code);
        result.put(MSG_TAG,msg);
        return result;
    }

    public static AjaxResult error(ResultCode resultCode) {
        return success(resultCode);
    }


    //————————————————响应警告—————————————————————

    public static AjaxResult warn(String msg) {
        AjaxResult result = new AjaxResult();
        result.put(CODE_TAG, Type.WARN.value());
        result.put(MSG_TAG,msg);
        return result;
    }

    public static AjaxResult warn(ResultCode resultCode) {
        AjaxResult result = new AjaxResult();
        result.put(CODE_TAG,resultCode.code());
        result.put(MSG_TAG,resultCode.msg());
        return result;
    }

    public static AjaxResult warn(String msg, Object data) {
        AjaxResult result = new AjaxResult();
        result.put(CODE_TAG, Type.WARN.value());
        result.put(MSG_TAG,msg);
        result.put(DATA_TAG,data);
        return result;
    }

    @Override
    public AjaxResult put(String key, Object value) {
        super.put(key, value);
        return this;
    }

    //————————————————条件返回—————————————————————

    public static AjaxResult toAjax(int row){
        return row > 0 ? AjaxResult.success() : AjaxResult.error();
    }

    public static AjaxResult toAjax(boolean flag){
        return flag ? AjaxResult.success() : AjaxResult.error();
    }

    /**
     * 初始化一个新创建的 AjaxResult 对象
     *
     * @param resultCode 结果码
     */
    public AjaxResult(ResultCode resultCode) {
        super.put(CODE_TAG, resultCode.code());
        super.put(MSG_TAG, resultCode.msg());
    }

    /**
     * 初始化一个新创建的 AjaxResult 对象
     *
     * @param type 状态类型
     * @param msg  返回内容
     */
    public AjaxResult(Type type, String msg) {
        super.put(CODE_TAG, type.value());
        super.put(MSG_TAG, msg);
    }

    /**
     * 初始化一个新创建的 AjaxResult 对象
     *
     * @param type 状态类型
     * @param msg  返回内容
     * @param data 数据对象
     */
    public AjaxResult(Type type, String msg, Object data) {
        super.put(CODE_TAG, type.value());
        super.put(MSG_TAG, msg);
        if (StringUtils.isNotNull(data)) {
            super.put(DATA_TAG, data);
        }
    }
    public <T> T getData(String key, TypeReference<T> typeReference) {
        Object data = get(key);	//默认是map
        String jsonString = JSON.toJSONString(data);
        T t = JSON.parseObject(jsonString, typeReference);
        return t;
    }
    public Integer getCode() {

        return (Integer) this.get("code");
    }
}
