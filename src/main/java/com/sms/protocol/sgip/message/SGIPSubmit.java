package com.sms.protocol.sgip.message;


import com.sms.protocol.utils.SGIPCodecUtil;
import io.netty.buffer.ByteBuf;
import lombok.Data;

/**
 * SGIP_SUBMIT 命令（SP -> SMG，向手机下发 MT 短消息）。
 * <p>
 * 字段顺序与长度严格对应《中国联合通信公司短消息网关系统接口协议 SGIP V1.2》
 * 表 4-17（4.2.3.3.1 Submit 命令的语法，TCP/专用 SGIP 承载方式）。
 * </p>
 */
@Data
public class SGIPSubmit implements SGIPMessage {
    private SGIPHeader header;

    // ==================== 协议字段（顺序严格对应规范表 4-17） ====================

    /** SP 接入号码，21 字节定长 Text */
    private String spNumber;

    /** 付费号码，21 字节定长 Text；手机号前加 "86"；为空表示由 UserNumber 用户付费；全 0 串表示 SP 付费 */
    private String chargeNumber;

    /** 接收短消息的手机数量，1 字节 Integer，取值 1~100 */
    private int userCount;

    /** 接收手机号数组，每个 21 字节定长 Text，手机号前加 "86" */
    private String[] userNumber;

    /** 企业代码，5 字节定长 Text，取值 0~99999 */
    private String corpId;

    /** 业务代码，10 字节定长 Text，由 SP 定义 */
    private String serviceType;

    /** 计费类型，1 字节 Integer */
    private int feeType;

    /** 收费值（单位：分），6 字节定长 Text，取值 0~99999 */
    private String feeValue;

    /** 赠送话费（单位：分），6 字节定长 Text，取值 0~99999 */
    private String givenValue;

    /** 代收费标志，1 字节 Integer：0=应收，1=实收 */
    private int agentFlag;

    /** 引起 MT 消息的原因，1 字节 Integer：
     *  0=MO 点播引起的第一条 MT；1=MO 点播引起的非第一条 MT；
     *  2=非 MO 点播引起的 MT；3=系统反馈引起的 MT */
    private int morelatetoMTFlag;

    /** 消息优先级，1 字节 Integer，0~9 从低到高，默认 0 */
    private int priority;

    /** 短消息寿命终止时间，16 字节定长 Text，格式 yymmddhhmmsstnnp（tnnp 固定 032+）；空=使用 SMSC 缺省值 */
    private String expireTime;

    /** 定时发送时间，16 字节定长 Text，格式同上；空=立即发送 */
    private String scheduleTime;

    /** 状态报告标记，1 字节 Integer：
     *  0=仅出错时返回状态报告（缺省）；1=无论成功失败都返回；
     *  2=不返回状态报告；3=仅包月计费不下发用户但要返回报告 */
    private int reportFlag;

    /** GSM 协议 TP_pid，1 字节 Integer，参见 GSM 03.40 §9.2.3.9，普通短信填 0 */
    private int tpPid;

    /** GSM 协议 TP_udhi，1 字节 Integer：0=消息内容不包含 UDH；1=包含 UDH（长短信分片必须置 1） */
    private int tpUdhi;

    /** 消息编码格式，1 字节 Integer：0=ASCII，3=写卡，4=二进制，8=UCS2，15=GBK */
    private int messageCoding;

    /** 信息类型，1 字节 Integer：0=短消息 */
    private int messageType;

    /** 消息内容长度，4 字节 Integer（大端），单位字节 */
    private int messageLength;

    /** 消息内容，变长，长度 = messageLength */
    private byte[] messageContent;

    /** 保留字段，固定 8 字节 */
    private byte[] reserve = new byte[8];

    @Override
    public void writeBody(ByteBuf out) {
        SGIPCodecUtil.writeCString(out, spNumber, 21);
        SGIPCodecUtil.writeCString(out, chargeNumber, 21);

        out.writeByte(userCount & 0xFF);
        for (String number : userNumber) {
            SGIPCodecUtil.writeCString(out, number, 21);
        }

        SGIPCodecUtil.writeCString(out, corpId, 5);
        SGIPCodecUtil.writeCString(out, serviceType, 10);

        out.writeByte(feeType & 0xFF);
        SGIPCodecUtil.writeCString(out, feeValue, 6);
        SGIPCodecUtil.writeCString(out, givenValue, 6);
        out.writeByte(agentFlag & 0xFF);
        out.writeByte(morelatetoMTFlag & 0xFF);
        out.writeByte(priority & 0xFF);

        SGIPCodecUtil.writeCString(out, expireTime, 16);
        SGIPCodecUtil.writeCString(out, scheduleTime, 16);

        out.writeByte(reportFlag & 0xFF);
        out.writeByte(tpPid & 0xFF);
        out.writeByte(tpUdhi & 0xFF);
        out.writeByte(messageCoding & 0xFF);
        out.writeByte(messageType & 0xFF);

        out.writeInt(messageLength);
        out.writeBytes(messageContent);

        out.writeBytes(reserve);
    }
}
