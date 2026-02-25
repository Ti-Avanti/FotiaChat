package gg.fotia.chat.util;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

public class TimeUtil {

    private static final DateTimeFormatter DEFAULT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * 获取当前时间戳（毫秒）
     */
    public static long now() {
        return System.currentTimeMillis();
    }

    /**
     * 格式化时间戳为默认格式
     */
    public static String format(long timestamp) {
        return format(timestamp, DEFAULT_FORMATTER);
    }

    /**
     * 格式化时间戳为指定格式
     */
    public static String format(long timestamp, DateTimeFormatter formatter) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
        return dateTime.format(formatter);
    }

    /**
     * 格式化时间戳为日期
     */
    public static String formatDate(long timestamp) {
        return format(timestamp, DATE_FORMATTER);
    }

    /**
     * 格式化时间戳为时间
     */
    public static String formatTime(long timestamp) {
        return format(timestamp, TIME_FORMATTER);
    }

    /**
     * 解析时间字符串为秒数
     * 支持格式: 1d, 2h, 30m, 60s, 1d2h30m
     */
    public static long parseTime(String timeString) {
        if (timeString == null || timeString.isEmpty()) {
            return 0;
        }

        long totalSeconds = 0;
        StringBuilder number = new StringBuilder();

        for (char c : timeString.toLowerCase().toCharArray()) {
            if (Character.isDigit(c)) {
                number.append(c);
            } else {
                if (number.length() > 0) {
                    long value = Long.parseLong(number.toString());
                    switch (c) {
                        case 'd' -> totalSeconds += TimeUnit.DAYS.toSeconds(value);
                        case 'h' -> totalSeconds += TimeUnit.HOURS.toSeconds(value);
                        case 'm' -> totalSeconds += TimeUnit.MINUTES.toSeconds(value);
                        case 's' -> totalSeconds += value;
                    }
                    number = new StringBuilder();
                }
            }
        }

        return totalSeconds;
    }

    /**
     * 将秒数格式化为可读字符串
     */
    public static String formatDuration(long seconds) {
        if (seconds <= 0) {
            return "0秒";
        }

        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("天");
        if (hours > 0) sb.append(hours).append("小时");
        if (minutes > 0) sb.append(minutes).append("分钟");
        if (secs > 0 || sb.length() == 0) sb.append(secs).append("秒");

        return sb.toString();
    }

    /**
     * 将秒数格式化为英文可读字符串
     */
    public static String formatDurationEn(long seconds) {
        if (seconds <= 0) {
            return "0s";
        }

        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (secs > 0 || sb.length() == 0) sb.append(secs).append("s");

        return sb.toString().trim();
    }

    /**
     * 计算两个时间戳之间的差值（秒）
     */
    public static long diff(long start, long end) {
        return Math.abs(end - start) / 1000;
    }

    /**
     * 检查时间是否已过期
     */
    public static boolean isExpired(long timestamp) {
        return timestamp > 0 && timestamp < now();
    }

    /**
     * 检查时间是否在指定秒数内
     */
    public static boolean isWithin(long timestamp, long seconds) {
        return diff(timestamp, now()) <= seconds;
    }
}
