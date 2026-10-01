package com.fc.v2.service;

import java.math.BigDecimal;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.THmfUseFlow;
import com.fc.v2.model.auto.THmfUseTrack;

/**
 * 维修资金使用申请单 Service接口（state-machine 形状：四档流转，不提供增删改查全套）
 *
 * 四档钉死：0立项 1公示 2施工 3结算，走到结算才算办结一趟。
 * 单据归在第几档、下一档收不收，只认推进/收回这一个入口回话；前端递上来的段号只当意图。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITHmfUseFlowService {

    /** 按主键查询单据（读段次旧法：参数与给回一概不动） */
    THmfUseFlow selectTHmfUseFlowById(Long id);

    /** 列表查询（流转台账） */
    List<THmfUseFlow> selectTHmfUseFlowList(QueryWrapper<THmfUseFlow> queryWrapper);

    /**
     * 立项起单：工程地点、预算金额、施工单位名称、申请来源四样齐才起得来，
     * 公示天数随单带死；同一份申请前一张还在办或已中止时，第二张起不来。被拒返回 null。
     */
    THmfUseFlow open(String bizNo, String workSite, BigDecimal budgetAmt, String builderName,
                     Integer applySource, Integer publishDays, String remark);

    /**
     * 往前一档（柜员手点来路）：只进紧挨的下一档；当前档照已过痕迹回放得出。
     * intentStage 是屏上带出的段号，只当意图：与回放对不齐（另一来路已先推过、同档报第二遍）时，
     * 不另起一行、不越档再推，给回现状那句话；门槛没过返回 null。
     */
    THmfUseFlow advance(Long id, Integer intentStage, String remark);

    /**
     * 往前一档（系统按公示天数自己推的来路）：只在公示天数走满后，把单从公示推进施工；
     * 与柜员手点并到同一个推进核心，屏上只许有一个说法。另起名字接，不在旧路上叠第二层。
     */
    THmfUseFlow clockAdvance(Long id);

    /**
     * 往后收回一档：收回来那档原先记的事沉底，重走另起一趟从这一档重新攒起，旧趟痕迹留存。
     * intentStage 只当意图，与回放不齐时给回现状、不重复收。头一档（立项）无处可收。被拒返回 null。
     */
    THmfUseFlow rollback(Long id, Integer intentStage, String remark);

    /** 作废：字改不得、张删不得；作废后续不动记事、交不上材料、走不了档。被拒返回 null */
    THmfUseFlow cancel(Long id, String remark);

    /**
     * 挂门槛材料（合同要件、验收记载、监理那一名、列支户面与列支额）：
     * 材料递得再齐也不自己过档，过档只认 advance/clockAdvance；已作废、已办结的单挂不进。被拒返回 null。
     * 各参传 null 表示不动那一格。
     */
    THmfUseFlow attach(Long id, Integer contractReady, String acceptRecord, String supervisorName,
                       Long acctId, BigDecimal payAmt);

    /**
     * 倒序复查名单：从结算倒回立项一档一档列全（旧趟在前、新趟在后各自分存），
     * 每笔带档号、趟次、动作、来路、操作人、时刻。单据不存在返回 null。
     */
    List<THmfUseTrack> listTrackTrail(Long id);

    /**
     * 走完那趟另有的一笔账：从结算倒回立项捋当前趟——
     * 多出一条记载、缺一档痕迹、或者事后由人补进去的，看到任一处就算没走通。
     */
    boolean verifyWalk(Long id);

    /** 修改经办记事：已作废、已办结不得再改，被拒返回 false */
    boolean updateContent(Long id, String remark);

    /** 删除单据：已作废、已办结的张删不得，被拒返回 false */
    boolean remove(Long id);
}
