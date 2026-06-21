package com.yzx.common.utils;

import cn.hutool.core.util.IdUtil;
import cn.idev.excel.FastExcel;
import cn.idev.excel.write.builder.ExcelWriterBuilder;
import cn.idev.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import com.yzx.common.excel.convert.ExcelBigNumberConvert;
import com.yzx.common.excel.core.DefaultExcelListener;
import com.yzx.common.excel.core.DropDownOptions;
import com.yzx.common.excel.core.ExcelListener;
import com.yzx.common.excel.core.ExcelResult;
import com.yzx.model.utils.FileUtils;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.List;

/**
 * @className: ExcelUtil
 * @author: yzx
 * @date: 2026/6/17 16:52
 * @Version: 1.0
 * @description:
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ExcelUtil {

    /**
     * 同步导入(适用于小数据量)
     */
    public static <T> List<T> importExcel(InputStream is, Class<T> clazz) {
        return FastExcel.read(is).head(clazz).autoCloseStream(false).sheet().doReadSync();
    }

    /**
     * 使用校验监听器 异步导入 同步返回
     *
     * @param is         输入流
     * @param clazz      对象类型
     * @param isValidate 是否 Validator 检验 默认为是
     * @return 转换后集合
     */
    public static <T> ExcelResult<T> importExcel(InputStream is, Class<T> clazz, boolean isValidate) {
        DefaultExcelListener<T> listener = new DefaultExcelListener<>(isValidate);
        FastExcel.read(is, clazz, listener).sheet().doRead();
        return listener.getExcelResult();
    }

    /**
     * 使用自定义监听器 异步导入 自定义返回
     *
     * @param is       输入流
     * @param clazz    对象类型
     * @param listener 自定义监听器
     * @return 转换后集合
     */
    public static <T> ExcelResult<T> importExcel(InputStream is, Class<T> clazz, ExcelListener<T> listener) {
        FastExcel.read(is, clazz, listener).sheet().doRead();
        return listener.getExcelResult();
    }


    /**
     *导出excel
     * @param list     导出数据集合
     * @param sheetName 工作表的名称
     * @param clazz  实体类
     * @param response 响应体
     */
    public static <T> void exportExcel(List<T> list, String sheetName, Class<T> clazz, HttpServletResponse response) {
        try {
            resetResponse(sheetName, response);
            ServletOutputStream outputStream = response.getOutputStream();
            exportExcel(list, sheetName, clazz, false, outputStream, null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> void exportExcel(List<T> list, String sheetName, Class<T> clazz, HttpServletResponse response, List<DropDownOptions> options) {
        try {
            resetResponse(sheetName, response);
            ServletOutputStream os = response.getOutputStream();
            exportExcel(list, sheetName, clazz, false, os, options);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 导出excel
     *
     * @param list      导出数据集合
     * @param sheetName 工作表的名称
     * @param clazz     实体类
     * @param os        输出流
     */
    public static <T> void exportExcel(List<T> list, String sheetName, Class<T> clazz, boolean merge, OutputStream os, List<DropDownOptions> options) {
        ExcelWriterBuilder builder = FastExcel.write(os, clazz)
                .autoCloseStream(false)
                .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                .registerConverter(new ExcelBigNumberConvert())
                .registerWriteHandler(new DataWriterHandler(clazz))
                .sheet(sheetName);
        if (merge) {
            //合并处理器
            builder.registerWriteHandler(new CellMergeStrategy(list, true));
        }
        //添加下拉框操作
        builder.registerWriteHandler(new ExcelDownHandler(options));
        builder.doWrite(list);
    }


    private static void resetResponse(String sheetName, HttpServletResponse response) throws UnsupportedEncodingException {
        String filename = encodingFilename(sheetName);
        FileUtils.setAttachmentResponseHeader(response, filename);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=UTF-8");
    }

    /**
     * 编码文件名
     */
    public static String encodingFilename(String filename) {
        return IdUtil.fastSimpleUUID() + "_" + filename + ".xlsx";
    }
}
