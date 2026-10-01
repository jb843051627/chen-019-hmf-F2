package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * 使用申请单过档痕迹流水对象 t_hmf_use_track
 * 过一道口一行，只添不删不抹；样式照 t_hmf_budget_sign，不另立新张。
 * 退回重走以 walk_no 另起一趟，旧趟原样留存。倒序复查就捋这一张。
 *
 * @author fuce
 * @date 2026-09-30
 */
@TableName("t_hmf_use_track")
@ApiModel(value = "THmfUseTrack", description = "使用申请单过档痕迹流水")
public class THmfUseTrack implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 所属使用申请单 */
    @TableField("flow_id")
    @ApiModelProperty(value = "所属使用申请单")
    private Long flowId;

    /** 本笔落在哪一档 0立项 1公示 2施工 3结算 */
    @TableField("stage_no")
    @ApiModelProperty(value = "本笔落在哪一档 0立项 1公示 2施工 3结算")
    private Integer stageNo;

    /** 第几趟（退回重走添新趟，旧趟保留） */
    @TableField("walk_no")
    @ApiModelProperty(value = "第几趟（退回重走添新趟，旧趟保留）")
    private Integer walkNo;

    /** 本笔动作 0往前 1往后 2作废 */
    @TableField("action")
    @ApiModelProperty(value = "本笔动作 0往前 1往后 2作废")
    private Integer action;

    /** 来路 0柜员手点 1系统按公示天数推 */
    @TableField("src")
    @ApiModelProperty(value = "来路 0柜员手点 1系统按公示天数推")
    private Integer src;

    /** 实际操作人/来路，逐笔实记（时钟来路记系统） */
    @TableField("operator")
    @ApiModelProperty(value = "实际操作人/来路")
    private String operator;

    /** 过口时刻（公示天数照本趟进公示行回放） */
    @TableField("act_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "过口时刻")
    private Date actTime;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注（退回事由等） */
    @TableField("remark")
    @ApiModelProperty(value = "备注（退回事由等）")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getFlowId() {
        return flowId;
    }

    public void setFlowId(Long flowId) {
        this.flowId = flowId;
    }

    public Integer getStageNo() {
        return stageNo;
    }

    public void setStageNo(Integer stageNo) {
        this.stageNo = stageNo;
    }

    public Integer getWalkNo() {
        return walkNo;
    }

    public void setWalkNo(Integer walkNo) {
        this.walkNo = walkNo;
    }

    public Integer getAction() {
        return action;
    }

    public void setAction(Integer action) {
        this.action = action;
    }

    public Integer getSrc() {
        return src;
    }

    public void setSrc(Integer src) {
        this.src = src;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public Date getActTime() {
        return actTime;
    }

    public void setActTime(Date actTime) {
        this.actTime = actTime;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
