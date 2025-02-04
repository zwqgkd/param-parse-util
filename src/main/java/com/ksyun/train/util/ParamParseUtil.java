package com.ksyun.train.util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;

public class ParamParseUtil {
    private final static Stack<Object> OBJ_STACK = new Stack<>();            //存放对象栈
    private final static Stack<Integer> PRE_SPACE_STACK = new Stack<>();       //存放缩进栈
    private final static Stack<Class<?>> INNER_CLAZZ_STACK = new Stack<>();     //存放泛型参数类型栈     //存放泛型参数类型

    public static int getPreSpaceNum(String s) {
        int preSpaceNum = 0;
        for (char c : s.toCharArray()) {
            if (c == ' ') {
                preSpaceNum++;
            } else {
                break;
            }
        }
        return preSpaceNum;
    }

    public static int getFirstIndexIgnoreFirstChar(String s, char c) {
        //find first index of letter

        for (int i = s.indexOf(c) + 1; i < s.length(); i++) {
            if (s.charAt(i) != ' ') {
                return i;
            }
        }
        return -1;
    }

    public static String uppercaseFirstChar(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        StringBuilder sb = new StringBuilder(str);
        sb.setCharAt(0, Character.toUpperCase(sb.charAt(0)));
        return sb.toString();
    }


    public static void handler(String trimLine, int curSpaceNum) throws Exception {
        //处理a:b 和 a:
        //topObj.a=new A()
        //如果有b set
        //如果无b 可能是array或者自定义，那么push进去
        String[] splits = Arrays.stream(trimLine.split(":")).map(String::trim).toArray(String[]::new);
        Object topObj = OBJ_STACK.peek();

        Field field = Arrays.stream(topObj.getClass().getDeclaredFields())
                .filter(f -> splits[0].equals(uppercaseFirstChar(f.getName())))
                .findFirst().orElse(null);
        if (field == null)
            return;

        //不提供set方法，那么就直接set
        if (!Modifier.isPublic(field.getModifiers())) {
            field.setAccessible(true);
        }
        //List ，自定义类型
        if (splits.length == 1) {
            // 检查字段是否是 List 类型或其子类
            if (List.class.isAssignableFrom(field.getType())) {
                Type type = field.getGenericType();
                if (type instanceof ParameterizedType) {
                    ParameterizedType pType = (ParameterizedType) type;
                    INNER_CLAZZ_STACK.push(Class.forName(pType.getActualTypeArguments()[0].getTypeName()));
                }
                OBJ_STACK.push(new ArrayList<>());
            } else {
                OBJ_STACK.push(field.getType().newInstance());
            }
            PRE_SPACE_STACK.push(curSpaceNum);
            field.set(topObj, OBJ_STACK.peek());
        } else {
            //含有value，那么直接需要set
            //排除skip的情况
            if (field.isAnnotationPresent(SkipMappingValueAnnotation.class)) {
                return;
            }
            if (splits[1].equals("null") || splits[1].equals("NULL") || splits[1].equals("Null")) {
                return;
            }
            //当发现对应的value是null或者字符串"NULL"，"null"、"Null"时，能正确处理原始类型默认值
            //SkipMappingValueAnnotation标注在属性上，工具类则不处理该属性，不去覆盖对象默认值
            //如果参数中的键值key是小写字符开头，请自动忽略，如参数中有cpu: 2，此时不应该将该值赋给对象中属性cpu，必须要求参数key都是大写的即Cpu: 2才进行适配。
            if (field.getType().equals(String.class)) {
                field.set(topObj, splits[1]);
            } else if (field.getType().equals(BigDecimal.class)) {
                field.set(topObj, new BigDecimal(splits[1]));
            } else if (field.getType().equals(int.class) || field.getType().equals(Integer.class)) {
                field.set(topObj, Integer.parseInt(splits[1]));
            } else if (field.getType().equals(boolean.class) || field.getType().equals(Boolean.class)) {
                field.set(topObj, Boolean.parseBoolean(splits[1]));
            } else if (field.getType().equals(long.class) || field.getType().equals(Long.class)) {
                field.set(topObj, Long.parseLong(splits[1]));
            } else if (field.getType().equals(double.class) || field.getType().equals(Double.class)) {
                field.set(topObj, Double.parseDouble(splits[1]));
            } else if (field.getType().equals(float.class) || field.getType().equals(Float.class)) {
                field.set(topObj, Float.parseFloat(splits[1]));
            } else if (field.getType().equals(short.class) || field.getType().equals(Short.class)) {
                field.set(topObj, Short.parseShort(splits[1]));
            } else if (field.getType().equals(byte.class) || field.getType().equals(Byte.class)) {
                field.set(topObj, Byte.parseByte(splits[1]));
            } else if (field.getType().equals(char.class) || field.getType().equals(Character.class)) {
                field.set(topObj, splits[1].charAt(0));
            } else {
                throw new RuntimeException("不支持的类型");
            }
        }

    }

    public static <T> T safeCast(Object obj, Class<T> type) {
        if (type.isInstance(obj)) {
            return type.cast(obj);
        } else {
            throw new ClassCastException("Cannot cast " + obj.getClass().getName() + " to " + type.getName());
        }
    }

    /**
     * "a:b"
     * "a:"
     * "-a:b"
     * "-a:"
     * "-b"
     * 需要有层级信息，根据缩进信息得到
     * 第一步：构造对象；
     * 1. 原始类型
     * 2. String, BigDecimal
     * 3. ArrayList
     * 4. 自定义Obj
     * 第二步：set
     * 需要对skip注解跳过
     * 1. value是空的, null, "NULL", "null", "Null"用默认值
     * 第三步：进入高级对象
     * 如何进入高级对象，需要有对象堆栈
     * 如果高级对象是：
     * 1. ArrayList
     * 2. 自定义Obj
     */
    public static <T> T parse(Class<T> clz, String filePath) throws Exception {
        //read file
        try (BufferedReader bis = new BufferedReader(new FileReader(filePath))) {
            OBJ_STACK.push(clz.newInstance());
            PRE_SPACE_STACK.push(-1);

            String line = null;
            while ((line = bis.readLine()) != null) {
                //跳过空行
                if (line.trim().isEmpty()) continue;
                System.out.println(line);
                //弹走栈里多余的
                /**
                 *  todo fixbug:由于-缩进问题，可能curSpaceNum<=PRE_SPACE_STACK.peek()，导致栈顶的list也被弹走
                 *              弹走同级的或者更低级的对象
                 *              如何判断同级或者更低级？
                 *              也就是缩进不小于当前行的缩进的对象&&当前行不是-开头
                 */
                int curSpaceNum = getPreSpaceNum(line);
                while (curSpaceNum < PRE_SPACE_STACK.peek() || curSpaceNum == PRE_SPACE_STACK.peek() && !line.trim().startsWith("-")) {
                    //如果出去的是ArrayList，那么需要pop innerClazzStack
                    if (List.class.isAssignableFrom(OBJ_STACK.peek().getClass())) {
                        INNER_CLAZZ_STACK.pop();
                    }
                    OBJ_STACK.pop();
                    PRE_SPACE_STACK.pop();
                }

                //看有无-
                if (line.trim().charAt(0) == '-') {
                    //todo fixbug:由于-格式缩进随意问题，导致栈顶可能是列表上一个类型，不是列表类型
                    while(!List.class.isAssignableFrom(OBJ_STACK.peek().getClass())){
                        OBJ_STACK.pop();
                        PRE_SPACE_STACK.pop();
                    }
                    //处理-b和- ， topObj.add(new A)
                    //如果是-，那么要入栈
                    //如果是-b, 那么要set
                    Object topObj = OBJ_STACK.peek();
                    //get add method
                    Method addMethod = topObj.getClass().getDeclaredMethod("add", Object.class);
                    //get parameterized type
                    //有-b的情况，需要set value
                    if (!line.contains(":")) {
                        //不是自定义的类型，那么是原始类型，String, BigDecimal
                        //含有value需要直接set,且这里不会有skip注解
                        //get value
                        String value = line.substring(getFirstIndexIgnoreFirstChar(line, '-')).trim();
                        //当发现对应的value是null或者字符串"NULL"，"null"、"Null"时，能正确处理原始类型默认值
                        if (!value.equals("null") && !value.equals("NULL") && !value.equals("Null")) {
                            if (String.class.isAssignableFrom(INNER_CLAZZ_STACK.peek())) {
                                addMethod.invoke(topObj, value);
                            } else if (BigDecimal.class.isAssignableFrom(INNER_CLAZZ_STACK.peek())) {
                                addMethod.invoke(topObj, new BigDecimal(value));
                            } else if (int.class.isAssignableFrom(INNER_CLAZZ_STACK.peek()) || Integer.class.isAssignableFrom(INNER_CLAZZ_STACK.peek())) {
                                addMethod.invoke(topObj, Integer.parseInt(value));
                            } else if (boolean.class.isAssignableFrom(INNER_CLAZZ_STACK.peek()) || Boolean.class.isAssignableFrom(INNER_CLAZZ_STACK.peek())) {
                                addMethod.invoke(topObj, Boolean.parseBoolean(value));
                            } else if (long.class.isAssignableFrom(INNER_CLAZZ_STACK.peek()) || Long.class.isAssignableFrom(INNER_CLAZZ_STACK.peek())) {
                                addMethod.invoke(topObj, Long.parseLong(value));
                            } else if (double.class.isAssignableFrom(INNER_CLAZZ_STACK.peek()) || Double.class.isAssignableFrom(INNER_CLAZZ_STACK.peek())) {
                                addMethod.invoke(topObj, Double.parseDouble(value));
                            } else if (float.class.isAssignableFrom(INNER_CLAZZ_STACK.peek()) || Float.class.isAssignableFrom(INNER_CLAZZ_STACK.peek())) {
                                addMethod.invoke(topObj, Float.parseFloat(value));
                            } else if (short.class.isAssignableFrom(INNER_CLAZZ_STACK.peek()) || Short.class.isAssignableFrom(INNER_CLAZZ_STACK.peek())) {
                                addMethod.invoke(topObj, Short.parseShort(value));
                            } else if (byte.class.isAssignableFrom(INNER_CLAZZ_STACK.peek()) || Byte.class.isAssignableFrom(INNER_CLAZZ_STACK.peek())) {
                                addMethod.invoke(topObj, Byte.parseByte(value));
                            } else if (char.class.isAssignableFrom(INNER_CLAZZ_STACK.peek()) || Character.class.isAssignableFrom(INNER_CLAZZ_STACK.peek())) {
                                addMethod.invoke(topObj, value.charAt(0));
                            } else {
                                throw new RuntimeException("不支持的类型");
                            }
                        }
                    } else {
                        addMethod.invoke(topObj, INNER_CLAZZ_STACK.peek().newInstance());
                        //入栈，处理-后面的
                        //get last of topObj and push to stack
                        PRE_SPACE_STACK.push(curSpaceNum);
                        OBJ_STACK.push(((List<?>) topObj).get(((List<?>) topObj).size() - 1));
                        handler(line.substring(line.indexOf("-") + 1).trim(), getFirstIndexIgnoreFirstChar(line, '-'));
                    }
                } else {
                    handler(line.trim(), curSpaceNum);
                }
            }
            //return the bottom of objStack
            return safeCast(OBJ_STACK.firstElement(), clz);
        }
    }
}