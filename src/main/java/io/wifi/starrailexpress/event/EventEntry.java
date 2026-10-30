package io.wifi.starrailexpress.event;

/**
 * 活动条目。
 *
 * @param id        唯一 id
 * @param title     活动标题
 * @param content   活动内容（\n 换行）
 * @param rewards   结构化奖励 "coin:500;shard:150;draw:3;skin:revolver/xxx"（; 分隔，可混合旧自由文本）
 * @param condition 获取条件：空=无条件，"kill:20"=击杀20人（任何阵营）
 * @param startDate 开始日期 yyyy-MM-dd（可空）
 * @param endDate   结束日期 yyyy-MM-dd（可空；有值时标题旁显示倒计时）
 * @param featured  是否推上标题（大活动，全局至多一个，服务端保证）
 * @param imageId   图片 id（空串 = 无图）
 */
public record EventEntry(String id, String title, String content, String rewards, String condition,
                         String startDate, String endDate, boolean featured,
                         String imageId, long createdAt) {
}