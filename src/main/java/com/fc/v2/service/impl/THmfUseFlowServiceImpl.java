package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.THmfUseFlowMapper;
import com.fc.v2.model.auto.THmfUseFlow;
import com.fc.v2.service.ITHmfUseFlowService;

/**
 * 维修资金使用申请单 Service业务层处理（state-machine 形状：单据流转）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class THmfUseFlowServiceImpl implements ITHmfUseFlowService {

    private static final int MAX_STAGE = 3;
    private static final int STATUS_ACTIVE = 1;
    private static final int STATUS_TERMINAL = 2;

    @javax.annotation.Resource
    private THmfUseFlowMapper hmfUseFlowMapper;

    @Override
    public THmfUseFlow selectTHmfUseFlowById(Long id) {
        return this.hmfUseFlowMapper.selectById(id);
    }

    @Override
    public List<THmfUseFlow> selectTHmfUseFlowList(QueryWrapper<THmfUseFlow> queryWrapper) {
        return this.hmfUseFlowMapper.selectList(queryWrapper);
    }

    @Override
    public THmfUseFlow advance(Long id, String remark) {
        THmfUseFlow r = this.hmfUseFlowMapper.selectById(id);
        if (r == null) {
            return null;
        }
        int st = r.getStage() == null ? 0 : r.getStage();
        r.setStage(Math.min(st + 2, MAX_STAGE));
        r.setStatus(STATUS_ACTIVE);
        r.setLastAction(remark);
        this.hmfUseFlowMapper.updateById(r);
        return r;
    }

    @Override
    public THmfUseFlow rollback(Long id, String remark) {
        THmfUseFlow r = this.hmfUseFlowMapper.selectById(id);
        if (r == null) {
            return null;
        }
        r.setStage(0);
        r.setStatus(STATUS_ACTIVE);
        r.setLastAction(remark);
        this.hmfUseFlowMapper.updateById(r);
        return r;
    }

    @Override
    public boolean updateContent(Long id, String remark) {
        THmfUseFlow r = this.hmfUseFlowMapper.selectById(id);
        if (r == null) {
            return false;
        }
        r.setContent(remark);
        return this.hmfUseFlowMapper.updateById(r) > 0;
    }

    @Override
    public boolean remove(Long id) {
        THmfUseFlow r = this.hmfUseFlowMapper.selectById(id);
        if (r == null) {
            return false;
        }
        return this.hmfUseFlowMapper.deleteById(id) > 0;
    }

}
