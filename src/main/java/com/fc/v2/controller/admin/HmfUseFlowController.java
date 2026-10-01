package com.fc.v2.controller.admin;

import java.math.BigDecimal;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.THmfUseFlow;
import com.fc.v2.service.ITHmfUseFlowService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 维修资金使用申请单 Controller（state-machine 形状：四档流转唯一入口）
 *
 * 单子归在第几档、下一档收不收，只认服务层推进/收回这一个入口回话；
 * 界面递上来的段号只当意图，这里不留第二个能直接改段的把手。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Api(value = "维修资金使用申请单")
@Controller
@RequestMapping("/hmfUseFlow")
public class HmfUseFlowController extends BaseController {

    private final String prefix = "admin/hmfUseFlow";

    @Autowired
    private ITHmfUseFlowService hmfUseFlowService;

    @ApiOperation(value = "流转台账跳转", notes = "流转台账跳转")
    @GetMapping("/view")
    @RequiresPermissions("hmfUseFlow:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "维修资金使用申请单流转台账", action = "list")
    @ApiOperation(value = "流转台账", notes = "流转台账")
    @GetMapping("/list")
    @RequiresPermissions("hmfUseFlow:list")
    @ResponseBody
    public ResultTable list(THmfUseFlow record) {
        QueryWrapper<THmfUseFlow> queryWrapper = new QueryWrapper<THmfUseFlow>();
        startPage();
        com.github.pagehelper.PageInfo<THmfUseFlow> page =
                new com.github.pagehelper.PageInfo<THmfUseFlow>(hmfUseFlowService.selectTHmfUseFlowList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "维修资金使用申请单立项", action = "open")
    @ApiOperation(value = "立项起单", notes = "工程地点、预算金额、施工单位名称、申请来源四样齐才起得来")
    @PostMapping("/open")
    @RequiresPermissions("hmfUseFlow:open")
    @ResponseBody
    public AjaxResult open(THmfUseFlow record, Integer publishDays) {
        THmfUseFlow r = hmfUseFlowService.open(record.getBizNo(), record.getWorkSite(), record.getBudgetAmt(),
                record.getBuilderName(), record.getApplySource(), publishDays, record.getRemark());
        return r == null ? AjaxResult.error("立项四样不齐，或同一份申请前一张还在办/已中止") : AjaxResult.successData(200, r);
    }

    @Log(title = "维修资金使用申请单推进", action = "advance")
    @ApiOperation(value = "往前一档（柜员手点）", notes = "只进紧挨的下一档；段号以服务层回放为准")
    @PostMapping("/advance")
    @RequiresPermissions("hmfUseFlow:advance")
    @ResponseBody
    public AjaxResult advance(Long id, Integer stage, String remark) {
        // stage 只是界面带出的意图，在第几档、能不能进，全由服务层那一个入口回话
        THmfUseFlow r = hmfUseFlowService.advance(id, stage, remark);
        return r == null ? AjaxResult.error("门槛没过或已封死，停在原档") : AjaxResult.successData(200, r);
    }

    @Log(title = "维修资金使用申请单定时推进", action = "clockAdvance")
    @ApiOperation(value = "公示天数走满系统自推", notes = "系统来路，与柜员手点并到同一推进核心")
    @PostMapping("/clockAdvance")
    @RequiresPermissions("hmfUseFlow:advance")
    @ResponseBody
    public AjaxResult clockAdvance(Long id) {
        THmfUseFlow r = hmfUseFlowService.clockAdvance(id);
        return r == null ? AjaxResult.error("公示天数没走完，或不归这一道门管") : AjaxResult.successData(200, r);
    }

    @Log(title = "维修资金使用申请单回退", action = "rollback")
    @ApiOperation(value = "往后收回一档", notes = "收回档原先记的事沉底，重走另起一趟")
    @PostMapping("/rollback")
    @RequiresPermissions("hmfUseFlow:rollback")
    @ResponseBody
    public AjaxResult rollback(Long id, Integer stage, String remark) {
        // stage 只是界面带出的意图，收回只由服务层那一个入口认定
        THmfUseFlow r = hmfUseFlowService.rollback(id, stage, remark);
        return r == null ? AjaxResult.error("头一档无处可收，或已封死") : AjaxResult.successData(200, r);
    }

    @Log(title = "维修资金使用申请单作废", action = "cancel")
    @ApiOperation(value = "作废", notes = "字改不得、张删不得、续不动记事")
    @PostMapping("/cancel")
    @RequiresPermissions("hmfUseFlow:cancel")
    @ResponseBody
    public AjaxResult cancel(Long id, String remark) {
        THmfUseFlow r = hmfUseFlowService.cancel(id, remark);
        return r == null ? AjaxResult.error("已作废或已办结，封死") : AjaxResult.successData(200, r);
    }

    @Log(title = "维修资金使用申请单挂材料", action = "attach")
    @ApiOperation(value = "挂门槛材料", notes = "只挂料不过档；合同/验收/监理/列支户面与额")
    @PostMapping("/attach")
    @RequiresPermissions("hmfUseFlow:advance")
    @ResponseBody
    public AjaxResult attach(Long id, Integer contractReady, String acceptRecord, String supervisorName,
                             Long acctId, BigDecimal payAmt) {
        THmfUseFlow r = hmfUseFlowService.attach(id, contractReady, acceptRecord, supervisorName, acctId, payAmt);
        return r == null ? AjaxResult.error("已作废或已办结，挂不进") : AjaxResult.successData(200, r);
    }

    @Log(title = "维修资金使用申请单复查", action = "trail")
    @ApiOperation(value = "倒序复查名单", notes = "从结算倒回立项，新趟在前旧趟在后")
    @GetMapping("/trail")
    @RequiresPermissions("hmfUseFlow:view")
    @ResponseBody
    public AjaxResult trail(Long id) {
        return AjaxResult.successData(200, hmfUseFlowService.listTrackTrail(id));
    }

    @Log(title = "维修资金使用申请单复查", action = "verifyWalk")
    @ApiOperation(value = "走完那趟另有一笔账", notes = "多记/缺痕/事后补，见任一处即没走通")
    @GetMapping("/verifyWalk")
    @RequiresPermissions("hmfUseFlow:view")
    @ResponseBody
    public AjaxResult verifyWalk(Long id) {
        return hmfUseFlowService.verifyWalk(id) ? AjaxResult.success("这趟走通") : AjaxResult.error("这趟没走通");
    }
}
