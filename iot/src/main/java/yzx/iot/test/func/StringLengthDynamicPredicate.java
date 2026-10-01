package yzx.iot.test.func;

/**
 * @className: StringLengthDynamicPredicate
 * @author: yzx
 * @date: 2026/9/26 22:22
 * @Version: 1.0
 * @description:
 */
public class StringLengthDynamicPredicate implements MyPredicate<String> {
    private final int minLength;

    public StringLengthDynamicPredicate(int minLength) {
        this.minLength = minLength;
    }

    @Override
    public boolean test(String s) {
        return s.length() > minLength;
    }
}
