package yzx.iot.test.func;

import java.util.Arrays;
import java.util.List;

/**
 * @className: FuncTest2
 * @author: yzx
 * @date: 2026/9/26 22:24
 * @Version: 1.0
 * @description:
 */
public class FuncTest2 {
    public static void main(String[] args) {
        //方法1:独立实现类很麻烦一般不使用
        List<String> sourceList = Arrays.asList("a", "bb", "ccc", "dddd");
        System.out.println("原始集合：" + sourceList);

        MyPredicate<String> p1 = new StringLengthPredicate();
        List<String> filter = MyFrameworkUtil.filter(sourceList, p1);

        MyPredicate<String> p2 = new StringLengthDynamicPredicate(3);
        List<String> filter2 = MyFrameworkUtil.filter(sourceList, p2);

        //方法2:匿名内部类 jdk8之前写法
        List<String> res3 = MyFrameworkUtil.filter(sourceList, new MyPredicate<String>() {
            @Override
            public boolean test(String s) {
                return s.startsWith("b");
            }
        });
        System.out.println(res3);

        //方法3:lamba表达式
        List<String> res4 = MyFrameworkUtil.filter(sourceList, s -> s.length() == 2);

        //方法4:方法引用
//        MyFrameworkUtil.filter(sourceList,StringLengthDynamicPredicate::test);
    }
}
