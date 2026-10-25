package io.wifi.starrailexpress.announcement;

/** 一条公告：标题、正文、发布者、发布时间（epoch 毫秒，展示时格式化为 年-月-日 时:分） */
public record AnnouncementEntry(String title, String content, String publisher, long timestamp) {
}