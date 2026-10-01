package yzx.iot.test.func;

import java.util.ArrayList;
import java.util.List;

/**
 * @className: MyFrameworkUtil
 * @author: yzx
 * @date: 2026/9/26 22:17
 * @Version: 1.0
 * @description:
 */
public class MyFrameworkUtil {
    public static <T> List<T> filter(List<T> list, MyPredicate<T> predicate) {
        List<T> resultList = new ArrayList<T>();
        for (T item : list) {
            if (predicate.test(item)) {
                resultList.add(item);
            }
        }
        return resultList;
    }
}
