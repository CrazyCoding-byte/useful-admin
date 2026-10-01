package yzx.iot.test;

/**
 * @className: Demo
 * @author: yzx
 * @date: 2026/9/26 20:05
 * @Version: 1.0
 * @description:
 * 类型    变量默认修饰	方法默认修饰	构造器	实例化	继承 / 实现
 * 接口 interface (JDK8 前)	public static final	public abstract	❌无	❌不能 new	多继承接口，类多实现
 * 接口 interface (JDK8+)	public static final	abstract:public abstract
 * default:public default
 * static:public static	❌无	❌不能 new	同上
 * 抽象类 abstract class	无默认，自定义	抽象方法 public abstract；普通方法自定义	✅有，只是不能 new	❌不能 new	单继承，可以多实现接口
 * 枚举 enum	常量：public static final；普通变量自定义	自定义	✅默认 private	❌外部不能 new	隐式继承 Enum，不能被普通类继承
 * 注解 @interface	注解属性（特殊成员）	不是方法，是注解属性	❌	❌	无继承
 */
public interface Demo {
    public static final int a = 0; //默认带public static final 要初始化

    void test();

    //    public abstract void test(); //这俩方法是等价的
//    private void test1();//不允许私有定义 私有定义必须要实现
    default void test2() {  //java 8 新增默认方法
        System.out.println("test2");
    }

    private void test3() {
        System.out.println("test3");
    }

    static void test4() { //不写就是public
        System.out.println("test4");
    }
}
