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
import java.math.BigDecimal;
import java.util.Date;

/**
 * 维修资金使用申请单对象 t_hmf_use_flow
 *
 * 四档钉死：0立项 1公示 2施工 3结算，走到结算才办结一趟。
 * 单子上的 stage 只是屏显镜像，准数由过档痕迹流水回放得出；前端递上来的段号只当意图。
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName("t_hmf_use_flow")
@ApiModel(value = "THmfUseFlow", description = "维修资金使用申请单")
public class THmfUseFlow implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 使用申请单号（同一份申请只许一张，前一张在办或已中止都不许再起第二张） */
    @TableField("biz_no")
    @ApiModelProperty(value = "使用申请单号")
    private String bizNo;

    /** 当前段次 0..3（立项/公示/施工/结算）；只照痕迹回放数出，纸面/屏显不算 */
    @TableField("stage")
    @ApiModelProperty(value = "当前段次 0..3（立项/公示/施工/结算）")
    private Integer stage;

    /** 当前趟次（每退回一次沉一档，重走另起一趟，旧趟痕迹留存） */
    @TableField("walk_no")
    @ApiModelProperty(value = "当前趟次")
    private Integer walkNo;

    /** 申领会落 0未起 1在办 2已作废 3已办结 */
    @TableField("status")
    @ApiModelProperty(value = "申领会落 0未起 1在办 2已作废 3已办结")
    private Integer status;

    /** 工程地点（立项四样之一） */
    @TableField("work_site")
    @ApiModelProperty(value = "工程地点")
    private String workSite;

    /** 预算金额（立项四样之一） */
    @TableField("budget_amt")
    @ApiModelProperty(value = "预算金额")
    private BigDecimal budgetAmt;

    /** 施工单位名称（立项四样之一） */
    @TableField("builder_name")
    @ApiModelProperty(value = "施工单位名称")
    private String builderName;

    /** 申请来源（立项四样之一）0物业报 1业委会报 */
    @TableField("apply_source")
    @ApiModelProperty(value = "申请来源 0物业报 1业委会报")
    private Integer applySource;

    /** 当地定的公示天数（进公示档时带死，重走另算） */
    @TableField("publish_days")
    @ApiModelProperty(value = "公示天数")
    private Integer publishDays;

    /** 本趟进公示档的时刻（镜像，准数以痕迹行 act_time 回放） */
    @TableField("publish_start")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "本趟进公示档的时刻")
    private Date publishStart;

    /** 合同要件挂齐没有 0未齐 1已齐（施工档的门槛） */
    @TableField("contract_ready")
    @ApiModelProperty(value = "合同要件挂齐没有 0未齐 1已齐")
    private Integer contractReady;

    /** 验收记载（结算档门槛之一） */
    @TableField("accept_record")
    @ApiModelProperty(value = "验收记载")
    private String acceptRecord;

    /** 监理那一名（结算档门槛之一） */
    @TableField("supervisor_name")
    @ApiModelProperty(value = "监理那一名")
    private String supervisorName;

    /** 本次列支额（进结算档时一次从户面余额划走） */
    @TableField("pay_amt")
    @ApiModelProperty(value = "本次列支额")
    private BigDecimal payAmt;

    /** 列支所走的分户账户面 */
    @TableField("acct_id")
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "列支所走的分户账户面")
    private Long acctId;

    /** 经办记事 */
    @TableField("content")
    @ApiModelProperty(value = "经办记事")
    private String content;

    /** 最近一次过口动作 */
    @TableField("last_action")
    @ApiModelProperty(value = "最近一次过口动作")
    private String lastAction;

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

    /** 备注 */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBizNo() {
        return bizNo;
    }

    public void setBizNo(String bizNo) {
        this.bizNo = bizNo;
    }

    public Integer getStage() {
        return stage;
    }

    public void setStage(Integer stage) {
        this.stage = stage;
    }

    public Integer getWalkNo() {
        return walkNo;
    }

    public void setWalkNo(Integer walkNo) {
        this.walkNo = walkNo;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getWorkSite() {
        return workSite;
    }

    public void setWorkSite(String workSite) {
        this.workSite = workSite;
    }

    public BigDecimal getBudgetAmt() {
        return budgetAmt;
    }

    public void setBudgetAmt(BigDecimal budgetAmt) {
        this.budgetAmt = budgetAmt;
    }

    public String getBuilderName() {
        return builderName;
    }

    public void setBuilderName(String builderName) {
        this.builderName = builderName;
    }

    public Integer getApplySource() {
        return applySource;
    }

    public void setApplySource(Integer applySource) {
        this.applySource = applySource;
    }

    public Integer getPublishDays() {
        return publishDays;
    }

    public void setPublishDays(Integer publishDays) {
        this.publishDays = publishDays;
    }

    public Date getPublishStart() {
        return publishStart;
    }

    public void setPublishStart(Date publishStart) {
        this.publishStart = publishStart;
    }

    public Integer getContractReady() {
        return contractReady;
    }

    public void setContractReady(Integer contractReady) {
        this.contractReady = contractReady;
    }

    public String getAcceptRecord() {
        return acceptRecord;
    }

    public void setAcceptRecord(String acceptRecord) {
        this.acceptRecord = acceptRecord;
    }

    public String getSupervisorName() {
        return supervisorName;
    }

    public void setSupervisorName(String supervisorName) {
        this.supervisorName = supervisorName;
    }

    public BigDecimal getPayAmt() {
        return payAmt;
    }

    public void setPayAmt(BigDecimal payAmt) {
        this.payAmt = payAmt;
    }

    public Long getAcctId() {
        return acctId;
    }

    public void setAcctId(Long acctId) {
        this.acctId = acctId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getLastAction() {
        return lastAction;
    }

    public void setLastAction(String lastAction) {
        this.lastAction = lastAction;
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

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
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
