package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.THmfAcctCardMapper;
import com.fc.v2.mapper.auto.THmfUseFlowMapper;
import com.fc.v2.mapper.auto.THmfUseTrackMapper;
import com.fc.v2.model.auto.THmfAcctCard;
import com.fc.v2.model.auto.THmfUseFlow;
import com.fc.v2.model.auto.THmfUseTrack;
import com.fc.v2.service.ITHmfUseFlowService;
import com.fc.v2.shiro.util.ShiroUtils;

/**
 * 维修资金使用申请单 Service业务层处理（state-machine 形状：四档流转）
 *
 * 口径（钉成四档）：0立项 1公示 2施工 3结算，一路走到结算才算办结一趟。
 * 立项四样齐才起得来（工程地点、预算金额、施工单位名称、申请来源）；公示档守当地公示天数，
 * 天数没走完，材料递得再齐也不算，施工那扇门开着也不能进；施工档看合同要件挂没挂齐；
 * 结算档看验收记载和监理那一名到没到，列支一落地就从户面余额划走，余额不够停在结算门外、整张账不动。
 *
 * 动作只有两种。往前只进紧挨的下一档，当前第几档由系统照过档痕迹一行一行回放得出，
 * 纸面/屏显写的段号只作参照，对不上时以回放为准。往后一次收一档，被收档原先记的事沉底，
 * 重走另起一趟（walk_no+1）从落回的档重新攒起，旧趟痕迹一行不抹。同档第二遍不另起一行，
 * 账上还是头一遍那句话。已作废的一张字改不得、张删不得、续不动记事。同一份申请前一张
 * 还在办或已中止，第二张起不来。柜员手点与系统按天数自己推，两条来路并到同一个推进核心，
 * 屏上只许有一个说法。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class THmfUseFlowServiceImpl implements ITHmfUseFlowService {

    /** 段次：立项 */
    private static final int STAGE_INITIATE = 0;
    /** 段次：公示 */
    private static final int STAGE_PUBLISH = 1;
    /** 段次：施工 */
    private static final int STAGE_BUILD = 2;
    /** 段次：结算（进得去即办结） */
    private static final int STAGE_SETTLE = 3;
    private static final int STAGE_TOP = STAGE_SETTLE;

    /** 申领会落：未起 */
    private static final int STATUS_UNBORN = 0;
    /** 申领会落：在办 */
    private static final int STATUS_ACTIVE = 1;
    /** 申领会落：已作废（中止） */
    private static final int STATUS_VOID = 2;
    /** 申领会落：已办结 */
    private static final int STATUS_DONE = 3;

    /** 申请来源：物业报 */
    private static final int SOURCE_PROPERTY = 0;
    /** 申请来源：业委会报 */
    private static final int SOURCE_COMMITTEE = 1;

    /** 过档动作：往前 */
    private static final int ACT_FORWARD = 0;
    /** 过档动作：往后 */
    private static final int ACT_BACK = 1;
    /** 过档动作：作废 */
    private static final int ACT_VOID = 2;

    /** 来路：柜员照老习惯手点 */
    private static final int SRC_TELLER = 0;
    /** 来路：系统按公示天数自己推 */
    private static final int SRC_CLOCK = 1;
    /** 时钟来路落在痕迹上的署名 */
    private static final String CLOCK_OPERATOR = "系统";

    private static final long MILLIS_OF_DAY = 24L * 60 * 60 * 1000;

    @javax.annotation.Resource
    private THmfUseFlowMapper hmfUseFlowMapper;

    @javax.annotation.Resource
    private THmfUseTrackMapper hmfUseTrackMapper;

    @javax.annotation.Resource
    private THmfAcctCardMapper hmfAcctCardMapper;

    @Override
    public THmfUseFlow selectTHmfUseFlowById(Long id) {
        return this.hmfUseFlowMapper.selectById(id);
    }

    @Override
    public List<THmfUseFlow> selectTHmfUseFlowList(QueryWrapper<THmfUseFlow> queryWrapper) {
        return this.hmfUseFlowMapper.selectList(queryWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public THmfUseFlow open(String bizNo, String workSite, BigDecimal budgetAmt, String builderName,
                            Integer applySource, Integer publishDays, String remark) {
        // 立项四样：一样不齐就起不来；公示天数照当地定数随单带死
        if (isBlank(bizNo) || isBlank(workSite) || isBlank(builderName)
                || budgetAmt == null || budgetAmt.compareTo(BigDecimal.ZERO) <= 0
                || applySource == null || (applySource != SOURCE_PROPERTY && applySource != SOURCE_COMMITTEE)
                || publishDays == null || publishDays < 0) {
            return null;
        }
        // 同一份申请：前一张还在办或已经中止（作废），第二张起不来；已办结的不拦着另起。
        List<THmfUseFlow> sameBiz = this.hmfUseFlowMapper.selectList(new QueryWrapper<THmfUseFlow>()
                .eq("biz_no", bizNo.trim()).eq("del_flag", 0));
        for (THmfUseFlow old : sameBiz) {
            if (Integer.valueOf(STATUS_ACTIVE).equals(old.getStatus())
                    || Integer.valueOf(STATUS_VOID).equals(old.getStatus())) {
                return null;
            }
        }
        THmfUseFlow r = new THmfUseFlow();
        r.setBizNo(bizNo.trim());
        r.setWorkSite(workSite.trim());
        r.setBudgetAmt(budgetAmt);
        r.setBuilderName(builderName.trim());
        r.setApplySource(applySource);
        r.setPublishDays(publishDays);
        r.setContractReady(0);
        r.setRemark(remark);
        r.setStage(STAGE_INITIATE);
        r.setWalkNo(1);
        r.setStatus(STATUS_ACTIVE);
        r.setDelFlag(0);
        this.hmfUseFlowMapper.insert(r);
        // 立项落定即本趟头一道痕迹，回放从这一行数起
        insertTrack(r.getId(), STAGE_INITIATE, 1, ACT_FORWARD, SRC_TELLER,
                ShiroUtils.getLoginName(), "立项", null, new Date());
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public THmfUseFlow advance(Long id, Integer intentStage, String remark) {
        // 柜员照老习惯手点的来路；屏上带出的段号只当意图，不当真
        return doAdvance(id, intentStage, remark, SRC_TELLER, ShiroUtils.getLoginName(), false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public THmfUseFlow clockAdvance(Long id) {
        // 系统按公示天数自己推的来路：只管公示→施工这一道门，另起名字接，仍并到同一个推进核心。
        return doAdvance(id, null, null, SRC_CLOCK, CLOCK_OPERATOR, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public THmfUseFlow rollback(Long id, Integer intentStage, String remark) {
        THmfUseFlow r = this.hmfUseFlowMapper.selectById(id);
        if (r == null || !isActive(r)) {
            return null;
        }
        List<THmfUseTrack> rows = tracksOf(id);
        int stage = replayStage(rows);
        int walk = replayWalk(rows);
        // 屏上带出的段号只当意图：它以为的档和系统回放的档对不齐（另一来路先动过、第二遍点）时，
        // 不再收一档，给回现状那句话，以入口回的为准。
        if (intentStage != null && intentStage != stage) {
            return this.hmfUseFlowMapper.selectById(id);
        }
        // 立项已是头一档，再收无处可收
        if (stage <= STAGE_INITIATE) {
            return null;
        }
        Date now = new Date();
        // 收回来那档（当前档）原先记的事沉底
        sinkWithdrawnStage(r, stage);
        // 落回公示的：本趟公示从收回这一刻重新计时；落回立项的：公示时刻一并沉底，重进时另算
        if (stage - 1 == STAGE_PUBLISH) {
            r.setPublishStart(now);
        } else {
            r.setPublishStart(null);
        }
        // 退回本身也逐笔留痕：写在旧趟里，旧趟一行不抹
        insertTrack(id, stage, walk, ACT_BACK, SRC_TELLER, ShiroUtils.getLoginName(), "往后", remark, now);
        r.setWalkNo(walk + 1);
        r.setStage(stage - 1);
        r.setStatus(STATUS_ACTIVE);
        r.setLastAction("往后");
        this.hmfUseFlowMapper.updateById(r);
        return this.hmfUseFlowMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public THmfUseFlow cancel(Long id, String remark) {
        THmfUseFlow r = this.hmfUseFlowMapper.selectById(id);
        if (r == null || !isActive(r)) {
            return null;
        }
        List<THmfUseTrack> rows = tracksOf(id);
        int stage = replayStage(rows);
        int walk = replayWalk(rows);
        insertTrack(id, stage, walk, ACT_VOID, SRC_TELLER, ShiroUtils.getLoginName(), "作废", remark, new Date());
        r.setStatus(STATUS_VOID);
        r.setStage(stage);
        r.setLastAction("作废");
        this.hmfUseFlowMapper.updateById(r);
        return this.hmfUseFlowMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public THmfUseFlow attach(Long id, Integer contractReady, String acceptRecord, String supervisorName,
                              Long acctId, BigDecimal payAmt) {
        THmfUseFlow r = this.hmfUseFlowMapper.selectById(id);
        // 已作废续不动记事、已办结交不上材料
        if (r == null || !isActive(r)) {
            return null;
        }
        if (payAmt != null && payAmt.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        if (contractReady != null) {
            r.setContractReady(contractReady == 1 ? 1 : 0);
        }
        if (acceptRecord != null) {
            r.setAcceptRecord(acceptRecord.trim().isEmpty() ? null : acceptRecord.trim());
        }
        if (supervisorName != null) {
            r.setSupervisorName(supervisorName.trim().isEmpty() ? null : supervisorName.trim());
        }
        if (acctId != null) {
            r.setAcctId(acctId);
        }
        if (payAmt != null) {
            r.setPayAmt(payAmt);
        }
        this.hmfUseFlowMapper.updateById(r);
        // 材料递得再齐也只挂在单上，绝不自己过档；过档只认推进入口
        return this.hmfUseFlowMapper.selectById(id);
    }

    @Override
    public List<THmfUseTrack> listTrackTrail(Long id) {
        if (id == null || this.hmfUseFlowMapper.selectById(id) == null) {
            return null;
        }
        // 从结算倒回立项：新趟在前、旧趟在后；同趟倒着点档；同档以落笔时刻定先后
        return this.hmfUseTrackMapper.selectList(new QueryWrapper<THmfUseTrack>()
                .eq("flow_id", id).eq("del_flag", 0)
                .orderByDesc("walk_no").orderByDesc("stage_no")
                .orderByAsc("act_time").orderByAsc("id"));
    }

    @Override
    public boolean verifyWalk(Long id) {
        THmfUseFlow r = id == null ? null : this.hmfUseFlowMapper.selectById(id);
        if (r == null || !Integer.valueOf(STATUS_DONE).equals(r.getStatus())) {
            return false;
        }
        // 落纸次序（id）即当时过口次序：从头把这台机器再开一遍，看能不能严丝合缝走到结算。
        List<THmfUseTrack> rows = tracksOf(id);
        rows.sort(Comparator.comparing(THmfUseTrack::getId));
        if (rows.isEmpty()) {
            return false;
        }
        // 头一行必须是第1趟立项落定
        THmfUseTrack head = rows.get(0);
        if (!Integer.valueOf(1).equals(head.getWalkNo())
                || !Integer.valueOf(STAGE_INITIATE).equals(head.getStageNo())
                || !Integer.valueOf(ACT_FORWARD).equals(head.getAction())) {
            return false;
        }
        int cur = STAGE_INITIATE;
        int walk = 1;
        Date prevTime = head.getActTime();
        java.util.Set<Long> forwardSeen = new java.util.HashSet<>();
        for (int i = 1; i < rows.size(); i++) {
            THmfUseTrack t = rows.get(i);
            // 事后由人补进去的：落纸次序（id）与落笔时刻（act_time）拧着，即为补痕
            if (t.getActTime() != null && prevTime != null && t.getActTime().before(prevTime)) {
                return false;
            }
            if (t.getActTime() != null) {
                prevTime = t.getActTime();
            }
            Integer w = t.getWalkNo();
            int action = t.getAction() == null ? -1 : t.getAction();
            int stage = t.getStageNo() == null ? -1 : t.getStageNo();
            if (action == ACT_VOID) {
                // 作废旧趟的行只留档，走通的一趟里见不得它
                return false;
            }
            if (action == ACT_FORWARD) {
                // 只许紧挨往前：跳档即缺一档痕迹
                if (w == null || w != walk || stage != cur + 1) {
                    return false;
                }
                // 同一趟同一档落第二道前行：多出一条记载
                if (!forwardSeen.add(pair(w, stage))) {
                    return false;
                }
                cur = stage;
            } else if (action == ACT_BACK) {
                // 收回只能收当时正落在的那一档；收一档，另起一趟
                if (w == null || w != walk || stage != cur) {
                    return false;
                }
                cur = stage - 1;
                walk++;
            } else {
                return false;
            }
        }
        // 一路捋到结算、一口不缺、一条不多，才算这趟走通
        return cur == STAGE_TOP;
    }

    private static long pair(int walk, int stage) {
        return ((long) walk << 32) ^ (stage & 0xffffffffL);
    }

    @Override
    public boolean updateContent(Long id, String remark) {
        THmfUseFlow r = this.hmfUseFlowMapper.selectById(id);
        if (r == null) {
            return false;
        }
        // 已经标了作废的那一张，字改不得；办结的档也不再记事
        if (Integer.valueOf(STATUS_VOID).equals(r.getStatus())
                || Integer.valueOf(STATUS_DONE).equals(r.getStatus())) {
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
        // 已经标了作废的那一张，张删不得（年末追列支当年凭的就是哪一版）；办结的张也留档
        if (Integer.valueOf(STATUS_VOID).equals(r.getStatus())
                || Integer.valueOf(STATUS_DONE).equals(r.getStatus())) {
            return false;
        }
        return this.hmfUseFlowMapper.deleteById(id) > 0;
    }

    /**
     * 推进唯一核心：柜员手点与系统时钟两条来路并到此处。
     * 屏上带出的段号一概不当真，当前档照痕迹回放；本档门槛没过一律拒，
     * 同一趟同一档第二遍到口不另起一行，给回头一遍那句话。
     */
    private THmfUseFlow doAdvance(Long id, Integer intentStage, String remark, int src,
                                  String operator, boolean clockOnly) {
        THmfUseFlow r = this.hmfUseFlowMapper.selectById(id);
        if (r == null || !isActive(r)) {
            return null;
        }
        List<THmfUseTrack> rows = tracksOf(id);
        int stage = replayStage(rows);
        int walk = replayWalk(rows);
        // 屏上带出的段号只当意图：以为还在的档与回放对不齐（另一来路手快、同档第二遍）时，
        // 不照它的意思越档再推，给回现状那句话，以入口回的为准。
        if (intentStage != null && intentStage != stage) {
            return this.hmfUseFlowMapper.selectById(id);
        }
        // 已走到结算的办结张，谁都推不动
        if (stage >= STAGE_TOP) {
            return null;
        }
        int next = stage + 1;
        if (clockOnly) {
            // 时钟来路只管公示天数走满后开施工这一扇门：还在立项，门不归它管
            if (next < STAGE_BUILD) {
                return null;
            }
            // 这一扇门不管是它还是柜员先推开的，既已推开，它给回的就是屏上同一句话，不另起一行
            if (next > STAGE_BUILD) {
                return this.hmfUseFlowMapper.selectById(id);
            }
        }
        // 下一档门槛：没过就停在门外，一条痕迹都不落
        Date now = new Date();
        if (!gateOpen(r, rows, next, now)) {
            return null;
        }
        // 同一档报第二遍：本趟本档那行已经在了，账上还是头一遍那句话，不另起一行。
        // 两条来路谁手快算谁的，后手只给同一句话。
        if (this.hmfUseTrackMapper.selectCount(new QueryWrapper<THmfUseTrack>()
                .eq("flow_id", id).eq("walk_no", walk).eq("stage_no", next)
                .eq("action", ACT_FORWARD).eq("del_flag", 0)) > 0) {
            return this.hmfUseFlowMapper.selectById(id);
        }
        insertTrack(id, next, walk, ACT_FORWARD, src, operator, "往前", remark, now);

        if (next == STAGE_PUBLISH) {
            // 本趟进公示的时刻带在单上做镜像，准数仍以痕迹行 act_time 回放
            r.setPublishStart(now);
        }
        if (next == STAGE_SETTLE) {
            // 列支一落地，钱从户面余额划走（进门门槛已验过余额够）；划走与办结同生共死
            THmfAcctCard acct = this.hmfAcctCardMapper.selectById(r.getAcctId());
            acct.setBalance(acct.getBalance().subtract(r.getPayAmt()));
            this.hmfAcctCardMapper.updateById(acct);
            r.setStatus(STATUS_DONE);
        }
        r.setStage(next);
        r.setWalkNo(walk);
        r.setLastAction("往前");
        this.hmfUseFlowMapper.updateById(r);
        return this.hmfUseFlowMapper.selectById(id);
    }

    /**
     * 下一档门槛。
     * 立项→公示：立项四样起单时已钉死，门开着。
     * 公示→施工：当地公示天数走完才算（材料递得再齐、施工门开着也不能进）。
     * 施工→结算：合同要件挂齐、验收记载与监理那一名都到、列支户面与列支额齐、户面余额够。
     */
    private boolean gateOpen(THmfUseFlow r, List<THmfUseTrack> rows, int next, Date now) {
        if (next == STAGE_PUBLISH) {
            return true;
        }
        if (next == STAGE_BUILD) {
            int walk = replayWalk(rows);
            Date start = null;
            Date reopen = null;
            for (THmfUseTrack t : rows) {
                Integer w = t.getWalkNo();
                if (w == null) {
                    continue;
                }
                // 本趟重新进公示的前行行：以它起算
                if (w == walk && Integer.valueOf(STAGE_PUBLISH).equals(t.getStageNo())
                        && Integer.valueOf(ACT_FORWARD).equals(t.getAction())) {
                    start = t.getActTime();
                }
                // 上一趟从施工退回收档：本趟重坐在公示的那一刻，从此重新计时
                if (w == walk - 1 && Integer.valueOf(STAGE_BUILD).equals(t.getStageNo())
                        && Integer.valueOf(ACT_BACK).equals(t.getAction())) {
                    reopen = t.getActTime();
                }
            }
            if (start == null) {
                start = reopen;
            }
            if (start == null) {
                return false;
            }
            int days = r.getPublishDays() == null ? 0 : r.getPublishDays();
            return now.getTime() - start.getTime() >= (long) days * MILLIS_OF_DAY;
        }
        if (next == STAGE_SETTLE) {
            // 合同要件挂没挂齐、验收记载和监理那一名到没到、列支户面与额齐不齐
            if (!Integer.valueOf(1).equals(r.getContractReady())
                    || isBlank(r.getAcceptRecord())
                    || isBlank(r.getSupervisorName())
                    || r.getAcctId() == null
                    || r.getPayAmt() == null || r.getPayAmt().compareTo(BigDecimal.ZERO) <= 0) {
                return false;
            }
            // 余额不够的单子停在结算门外，整张账不动（连一道过档痕迹都不落）
            THmfAcctCard acct = this.hmfAcctCardMapper.selectById(r.getAcctId());
            return acct != null && acct.getBalance() != null
                    && acct.getBalance().compareTo(r.getPayAmt()) >= 0;
        }
        return false;
    }

    /** 被收回的那一档（当前档）原先记的事沉底，重走时从落回的档重新攒起。 */
    private void sinkWithdrawnStage(THmfUseFlow r, int withdrawnStage) {
        if (withdrawnStage == STAGE_BUILD) {
            // 施工档挂的那一摊：合同要件、验收记载、监理、列支户面与额，全部沉底重攒
            r.setContractReady(0);
            r.setAcceptRecord(null);
            r.setSupervisorName(null);
            r.setAcctId(null);
            r.setPayAmt(null);
        }
        // 结算进得去即办结，没有从结算收回的情形；公示档自己没有挂的料，计时由调用处另算
    }

    /** 照痕迹一行一行回放当前档：往前落在哪档就是哪档，往后从该档收回一格。 */
    private int replayStage(List<THmfUseTrack> rows) {
        int stage = STAGE_INITIATE;
        for (THmfUseTrack t : rows) {
            int action = t.getAction() == null ? ACT_FORWARD : t.getAction();
            int at = t.getStageNo() == null ? stage : t.getStageNo();
            if (action == ACT_FORWARD) {
                stage = at;
            } else if (action == ACT_BACK) {
                stage = at - 1;
            }
        }
        return stage;
    }

    /** 当前趟次照痕迹数；末笔是退回时，下一笔落在新趟。 */
    private int replayWalk(List<THmfUseTrack> rows) {
        int walk = 1;
        THmfUseTrack last = null;
        for (THmfUseTrack t : rows) {
            if (t.getWalkNo() != null && t.getWalkNo() > walk) {
                walk = t.getWalkNo();
            }
            last = t;
        }
        if (last != null && Integer.valueOf(ACT_BACK).equals(last.getAction()) && last.getWalkNo() != null) {
            return last.getWalkNo() + 1;
        }
        return walk;
    }

    private List<THmfUseTrack> tracksOf(Long flowId) {
        return this.hmfUseTrackMapper.selectList(new QueryWrapper<THmfUseTrack>()
                .eq("flow_id", flowId).eq("del_flag", 0).orderByAsc("id"));
    }

    private void insertTrack(Long flowId, int stage, int walk, int action, int src,
                             String operator, String actionWord, String remark, Date actTime) {
        THmfUseTrack t = new THmfUseTrack();
        t.setFlowId(flowId);
        t.setStageNo(stage);
        t.setWalkNo(walk);
        t.setAction(action);
        t.setSrc(src);
        t.setOperator(operator);
        t.setActTime(actTime);
        t.setDelFlag(0);
        t.setRemark(remark == null ? actionWord : actionWord + "：" + remark);
        this.hmfUseTrackMapper.insert(t);
    }

    private boolean isActive(THmfUseFlow r) {
        Integer st = r.getStatus();
        // 历史单 status 可能为空，按在办兜底；作废、办结一律封死
        return !Integer.valueOf(STATUS_VOID).equals(st) && !Integer.valueOf(STATUS_DONE).equals(st);
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
