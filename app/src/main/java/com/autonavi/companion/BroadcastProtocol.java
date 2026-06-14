package com.autonavi.companion;

import android.text.TextUtils;

final class BroadcastProtocol {
    private static final String ACTION_SEND = "AUTONAVI_STANDARD_BROADCAST_SEND";
    private static final String ACTION_RECV = "AUTONAVI_STANDARD_BROADCAST_RECV";

    private BroadcastProtocol() {
    }

    static Info describe(BroadcastEvent event) {
        if (event == null) {
            return new Info("未知广播", "", "", -1, "没有事件数据。");
        }
        return describe(event.action, event.summary.keyType);
    }

    static Info describe(String action, int keyType) {
        String direction = directionLabel(action);
        switch (keyType) {
            case AmapConstants.KEY_TYPE_NAVIGATION_STATE:
                return new Info(direction, "3.1 地图状态发送 / 2.5.1 地图状态查询结果",
                        "KEY_TYPE=10019", keyType,
                        "EXTRA_STATE 运行状态：3 前台、4 后台、8 开始导航、9 结束导航、24 进入巡航、25 退出巡航等。");
            case AmapConstants.KEY_TYPE_ROUTE_GUIDANCE:
                return new Info(direction, "3.3 引导信息透出",
                        "KEY_TYPE=10001", keyType,
                        "导航/巡航/模拟导航中透出路名、转向图标、剩余距离时间、电子眼、限速、车速、道路类型等字段。");
            case AmapConstants.KEY_TYPE_LANE_INFO:
                return new Info(direction, "3.4 车道信息",
                        "KEY_TYPE=13012", keyType,
                        "EXTRA_DRIVE_WAY 车道线 JSON；通常由 RECV KEY_TYPE=10062 请求，SEND KEY_TYPE=13012 返回。");
            case 13011:
                return new Info(direction, "3.5 实时交通光柱图",
                        "KEY_TYPE=13011", keyType,
                        "EXTRA_TMC_SEGMENT 路况柱状图 JSON，包含总路程、剩余路程、分段路况颜色和比例。");
            case 12011:
                return new Info(direction, "3.10 高速出口信息",
                        "KEY_TYPE=12011", keyType,
                        "RECV 发送 EXIT_INFO_TYPE 查询高速/城快出口；SEND 返回出口编号、方向、距离、时间和结果状态。");
            case 10061:
                return new Info(direction, "3.2 请求最后一次地图状态",
                        "KEY_TYPE=10061", keyType,
                        "第三方请求后，高德返回最近一次地图状态消息。");
            case AmapConstants.KEY_TYPE_REQUEST_LANE:
                return new Info(direction, "3.4 车道信息查询",
                        "KEY_TYPE=10062", keyType,
                        "第三方请求车道线信息；高德通常用 SEND KEY_TYPE=13012 返回 EXTRA_DRIVE_WAY。");
            case AmapConstants.KEY_TYPE_CLUSTER_ACTIVATE:
                return new Info(direction, "2.5.9 激活状态查询结果",
                        "KEY_TYPE=13014", keyType,
                        "返回激活状态 EXTRA_ACTIVATE_STATE 和设备 ID 等信息。");
            case 12404:
                return new Info(direction, "2.5.1 请求查询地图状态",
                        "KEY_TYPE=12404", keyType,
                        "EXTRA_REQUEST_AUTO_STATE：0 查询前后台，1 查询导航状态，2 查询导航路线信息。");
            case 10041:
                return new Info(direction, "2.5.7 软件信息通知",
                        "KEY_TYPE=10041", keyType,
                        "高德启动时透出版本号 VERSION_NUM 和渠道号 CHANNEL_NUM。");
            case 10014:
                return new Info(direction, "7.1 油量信息",
                        "KEY_TYPE=10014", keyType,
                        "SEND 请求油量；RECV 传入油量百分比、告警状态、剩余里程、油箱大小等。");
            case 10017:
                return new Info(direction, "7.2.2 系统通知大灯状态",
                        "KEY_TYPE=10017", keyType,
                        "EXTRA_HEADLIGHT_STATE：0 开启，1 关闭；用于自动昼夜模式。");
            case 10073:
                return new Info(direction, "7.3 ACC ON 通知",
                        "KEY_TYPE=10073", keyType,
                        "点火后通知高德系统启动，高德可启用定位等服务。");
            case 10018:
                return new Info(direction, "7.4 ACC OFF 通知",
                        "KEY_TYPE=10018", keyType,
                        "熄火或系统关闭前通知高德保存相关数据。");
            case AmapConstants.KEY_TYPE_TRAFFIC_LIGHT:
                return new Info(direction, "扩展：红绿灯倒计时",
                        "KEY_TYPE=60073", keyType,
                        "20180813 标准协议未列出该 KEY；常见于新高德/包装层扩展，包含红绿灯方向、状态、倒计时或 lightsData。");
            case AmapConstants.KEY_TYPE_CRUISE:
                return new Info(direction, "扩展：巡航信息",
                        "KEY_TYPE=60021", keyType,
                        "20180813 标准协议未列出该 KEY；本应用按巡航道路、车速、红绿灯等字段解析。");
            default:
                if (action != null && action.startsWith("com.autonavi.amapauto.AUTO_WIDGET_")) {
                    return new Info("高德车机 Widget 广播", "内部 Widget 信息",
                            keyType >= 0 ? "KEY_TYPE=" + keyType : "", keyType,
                            "不是 20180813 标准广播 action；高德车机用于桌面/小组件道路、GPS、电子眼、红绿灯等状态同步。");
                }
                if ("AUTO_GUIDE_INFO_FOR_INTERNAL_WIDGET".equals(action)
                        || "AUTO_STATUS_FOR_INTERNAL_WIDGET".equals(action)) {
                    return new Info("高德内部 Widget 广播", "内部导航/状态信息",
                            keyType >= 0 ? "KEY_TYPE=" + keyType : "", keyType,
                            "不是 20180813 标准广播 action；字段通常与引导信息、导航状态或桌面组件状态有关。");
                }
                return new Info(direction, "未匹配到已知章节",
                        keyType >= 0 ? "KEY_TYPE=" + keyType : "无 KEY_TYPE", keyType,
                        "已记录原始字段；可导出诊断包后按 action 和 KEY_TYPE 对照协议文档继续分析。");
        }
    }

    private static String directionLabel(String action) {
        if (ACTION_SEND.equals(action)) {
            return "高德标准广播 SEND";
        }
        if (ACTION_RECV.equals(action)) {
            return "高德标准广播 RECV";
        }
        if (TextUtils.isEmpty(action)) {
            return "未知 action";
        }
        return "action=" + action;
    }

    static final class Info {
        final String direction;
        final String name;
        final String keyLabel;
        final int keyType;
        final String detail;

        Info(String direction, String name, String keyLabel, int keyType, String detail) {
            this.direction = direction == null ? "" : direction;
            this.name = name == null ? "" : name;
            this.keyLabel = keyLabel == null ? "" : keyLabel;
            this.keyType = keyType;
            this.detail = detail == null ? "" : detail;
        }

        String shortLine() {
            if (!TextUtils.isEmpty(name)) {
                return name;
            }
            return TextUtils.isEmpty(keyLabel) ? "未知协议" : keyLabel;
        }
    }
}
