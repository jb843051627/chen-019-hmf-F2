package com.fc.v2.controller.admin;

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
 * 维修资金使用申请单 Controller（state-machine 形状：流转入口）
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

    @Log(title = "维修资金使用申请单推进", action = "advance")
    @ApiOperation(value = "推进一档", notes = "推进一档")
    @PostMapping("/advance")
    @RequiresPermissions("hmfUseFlow:advance")
    @ResponseBody
    public AjaxResult advance(Long id, String remark) {
        return toAjax(hmfUseFlowService.advance(id, remark) != null ? 1 : 0);
    }

    @Log(title = "维修资金使用申请单回退", action = "rollback")
    @ApiOperation(value = "回退一档", notes = "回退一档")
    @PostMapping("/rollback")
    @RequiresPermissions("hmfUseFlow:rollback")
    @ResponseBody
    public AjaxResult rollback(Long id, String remark) {
        return toAjax(hmfUseFlowService.rollback(id, remark) != null ? 1 : 0);
    }
}
