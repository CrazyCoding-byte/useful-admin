package yzx.iot.test.func;

/**
 * @className: StringLengthPredicate
 * @author: yzx
 * @date: 2026/9/26 22:08
 * @Version: 1.0
 * @description:
 */
public class StringLengthPredicate implements MyPredicate<String> {
    @Override
    public boolean test(String s) {
        return s.length() > 1;
    }
}
