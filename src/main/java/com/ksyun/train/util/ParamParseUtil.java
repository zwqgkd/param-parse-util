package com.ksyun.train.util;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;
import java.util.function.Function;

public class ParamParseUtil {
    private final static Stack<Object>      objStack = new Stack<>();            //存放对象栈
    private final static Stack<Integer>     preSpaceStack = new Stack<>();       //存放缩进栈
    private final static Stack<Class<?>>    innerClazzStack = new Stack<>();     //存放泛型参数类型栈
    private static Class<?>                 innerClazz = null;                   //存放泛型参数类型

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

    public static int getFirstIndexIgnoreChar(String s, char c) {
        //find first index of letter
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) != c && s.charAt(i) != ' ') {
                return i;
            }
        }
        return -1;
    }

    public static String lowercaseFirstChar(String s) {
        if (s == null || s.isEmpty()) {
            return s; // 如果输入字符串为空或null，返回原字符串
        }

        char firstChar = s.charAt(0);
        char lowerFirstChar = Character.toLowerCase(firstChar);

        if (s.length() == 1) {
            return String.valueOf(lowerFirstChar); // 如果字符串只有一个字符
        }

        return lowerFirstChar + s.substring(1); // 处理多字符的字符串
    }

    public static <T> void handler(Class<T> clz, String trimLine, int curSpaceNum) throws Exception {
        //处理a:b 和 a:
        //todo topObj.a=new A()
        //如果有b set
        //如果无b 可能是array或者自定义，那么push进去
        String[] splits = Arrays.stream(trimLine.split(":")).map(String::trim).toArray(String[]::new);
        Object topObj = objStack.peek();

        Field field = topObj.getClass().getDeclaredField(lowercaseFirstChar(splits[0]));
        //不提供set方法，那么就直接set
        if (!Modifier.isPublic(field.getModifiers())) {
            field.setAccessible(true);
        }
        if (splits.length == 1) {
            // 检查字段是否是 List 类型或其子类
            if (List.class.isAssignableFrom(field.getType())) {
                //set paramterizedStr
/*                innerClazz = Arrays.stream(topObj.getClass().getDeclaredClasses())
                        .filter(e -> e.getSimpleName().equals(splits[0])).
                        findFirst().orElse(null);*/
                //如果不是自定义内部类
//                if(innerClazz==null){
                //Integer, String, BigDecimal
                //spilts[0]="command"
                //需要在container里面找到command
                //objStack=container
                //field=command
                Type type = field.getGenericType();
                if (type instanceof ParameterizedType) {
                    ParameterizedType pType = (ParameterizedType) type;
                    innerClazzStack.push(Class.forName(pType.getActualTypeArguments()[0].getTypeName()));
                }
                objStack.push(new ArrayList<>());
            } else {
                objStack.push(field.getType().newInstance());
            }
            preSpaceStack.push(curSpaceNum);
            field.set(topObj, objStack.peek());
        } else {
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

    /**
     * "a:b"
     * "a:"
     * "-a:b"
     * "-a:"
     * "-b"
     * 需要有层级信息，根据缩进信息得到
     * 第一步：构造对象；
     *      1. 原始类型
     *      2. String, BigDecimal
     *      3. ArrayList
     *      4. 自定义Obj
     * 第二步：set
     *      需要对skip注解跳过
     *      1. value是空的, null, "NULL", "null", "Null"用默认值
     * 第三步：进入高级对象
     *      如何进入高级对象，需要有对象堆栈
     *      如果高级对象是：
     *      1. ArrayList
     *      2. 自定义Obj
     */
    public static <T> T parse(Class<T> clz, String filePath) throws Exception {
        //read file
        try (BufferedReader bis = new BufferedReader(new FileReader(filePath))) {
            objStack.push(clz.newInstance());
            preSpaceStack.push(-1);

            String line;
            while ((line = bis.readLine()) != null) {
                //跳过空行
                if (line.trim().isEmpty()) continue;
                System.out.println(line);
                //todo 第一步
                //弹走栈里多余的
                int curSpaceNum = getPreSpaceNum(line);
                while (curSpaceNum <= preSpaceStack.peek()) {
                    //如果出去的是ArrayList，那么需要pop innerClazzStack
                    if (List.class.isAssignableFrom(objStack.peek().getClass())) {
                        innerClazzStack.pop();
                    }
                    objStack.pop();
                    preSpaceStack.pop();
                }

                //看有无-
                if (line.trim().charAt(0) == '-') {
                    //todo 处理-b和- ， topObj.add(new A)
                    //如果是-，那么要入栈
                    //如果是-b, 那么要set
                    Object topObj = objStack.peek();

                    //get add method
                    Method addMethod = topObj.getClass().getDeclaredMethod("add", Object.class);
                    //get parameterized type
                    //-b
                    if (line.indexOf(":") == -1) {
                        //todo 不是自定义的类型，那么是原始类型，String, BigDecimal
                        //get value
                        String value = line.substring(getFirstIndexIgnoreChar(line, '-')).trim();
                        if (String.class.isAssignableFrom(innerClazzStack.peek())) {
                            addMethod.invoke(topObj, value);
                        }
                    } else {
                        addMethod.invoke(topObj, innerClazzStack.peek().newInstance());
                        //todo 入栈，处理-后面的
                        //get last of topObj and push to stack
                        preSpaceStack.push(curSpaceNum);
                        objStack.push(((List) topObj).get(((List) topObj).size() - 1));
                        handler(clz, line.substring(line.indexOf("-") + 1).trim(), getFirstIndexIgnoreChar(line, '-'));
                    }
                } else {
                    handler(clz, line.trim(), curSpaceNum);
                }
                //todo 第二步
            }
            //return the bottom of objStack
            return (T) objStack.firstElement();
        }
    }
}