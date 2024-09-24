package com.xxyy.utils;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.RandomStringUtils;

/**
 * @author xy
 * @date 2024-09-17 21:45
 */

public class StringTools {

    public static String  getRandomNumber(int length) {
        return RandomStringUtils.random(length, false, true);
    }

    public static String getRandomString(int length) {
        return RandomStringUtils.random(length, true, true);
    }

    public static boolean isEmpty(String str) {
        if (null == str || "".equals(str) || "null".equals(str) || "\u0000".equals(str)) {
            return true;
        } else if ("".equals(str.trim())) {
            return true;
        }
        return false;
    }

    public static String byMd5(String oString) {
        return isEmpty(oString)? null: DigestUtils.md5Hex(oString);
    }

    public static String getFileSuffix(String fileName) {
        String[] split = fileName.split("\\.");
        return split[1];
    }
}
