package com.fc.v2.hmf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fc.v2.model.auto.THmfAcctCard;
import com.fc.v2.model.auto.THmfUseFlow;
import com.fc.v2.model.auto.THmfUseTrack;
import com.fc.v2.service.impl.THmfUseFlowServiceImpl;

/**
 * 使用申请单四档链逐条核对：
 * 立项四样、公示天数守门、施工看合同、结算看验收监理与户面余额、列支划账；
 * 段次照痕迹回放（纸面/屏显不算）、两条来路一个说法、同档第二遍不另起一行；
 * 收回一档沉底另起一趟、作废封死禁改禁删、同申请不起第二张；倒序复查多记/缺痕/事后补。
 */
class UseFlowWalkTest {

    private static final int PUBLISH = 1;
    private static final int BUILD = 2;
    private static final int SETTLE = 3;

    private static final int ACTIVE = 1;
    private static final int VOID = 2;
    private static final int DONE = 3;

    private static final int SRC_TELLER = 0;
    private static final int SRC_CLOCK = 1;

    private static final int FWD = 0;
    private static final int BACK = 1;
    private static final int CANCEL = 2;

    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private THmfUseFlowServiceImpl service;
    private FakeUseFlowMapper flows;
    private FakeUseTrackMapper tracks;
    private FakeAcctCardMapper accts;

    @BeforeEach
    void setUp() throws Exception {
        this.service = new THmfUseFlowServiceImpl();
        this.flows = new FakeUseFlowMapper();
        this.tracks = new FakeUseTrackMapper();
        this.accts = new FakeAcctCardMapper();
        inject("hmfUseFlowMapper", flows);
        inject("hmfUseTrackMapper", tracks);
        inject("hmfAcctCardMapper", accts);
    }

    private void inject(String name, Object value) throws Exception {
        Field f = THmfUseFlowServiceImpl.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(service, value);
    }

    // ---------- 立项 ----------

    @Test
    void open_fourItemsComplete_startsAtInitiationWithFirstTrack() {
        THmfUseFlow r = open("SY-2026-001", 7);
        assertNotNull(r.getId());
        assertEquals(0, r.getStage());
        assertEquals(1, r.getWalkNo());
        assertEquals(ACTIVE, r.getStatus());
        assertEquals("屋面（3号楼顶层）", r.getWorkSite());
        assertEquals(0, new BigDecimal("128000.00").compareTo(r.getBudgetAmt()));
        assertEquals("顺达建工", r.getBuilderName());
        assertEquals(1, r.getApplySource());
        assertEquals(7, r.getPublishDays());
        List<THmfUseTrack> ts = tracks.all();
        assertEquals(1, ts.size());
        assertTrack(ts.get(0), 0, 1, FWD, SRC_TELLER);
    }

    @Test
    void open_anyOfFourItemsMissing_rejected() {
        assertNull(service.open(null, "屋面", amt("1"), "顺达", 0, 7, null));
        assertNull(service.open("X", "  ", amt("1"), "顺达", 0, 7, null));
        assertNull(service.open("X", "屋面", null, "顺达", 0, 7, null));
        assertNull(service.open("X", "屋面", amt("0"), "顺达", 0, 7, null));
        assertNull(service.open("X", "屋面", amt("-1"), "顺达", 0, 7, null));
        assertNull(service.open("X", "屋面", amt("1"), " ", 0, 7, null));
        assertNull(service.open("X", "屋面", amt("1"), "顺达", null, 7, null));
        assertNull(service.open("X", "屋面", amt("1"), "顺达", 9, 7, null));
        assertNull(service.open("X", "屋面", amt("1"), "顺达", 0, null, null));
        assertNull(service.open("X", "屋面", amt("1"), "顺达", 0, -1, null));
        assertEquals(0, flows.all().size());
        assertEquals(0, tracks.all().size());
    }

    @Test
    void open_sameApplicationSecondSheetBlockedWhileActiveOrVoid() {
        assertNotNull(open("SY-1", 7));
        assertNull(open("SY-1", 7), "前一张还在办，第二张起不来");
        Long id = flows.all().get(0).getId();
        assertNotNull(service.cancel(id, "不做了"));
        assertNull(open("SY-1", 7), "前一张已中止（作废），照样起不来");
    }

    @Test
    void open_sameApplicationAfterSettled_canOpenNewSheet() {
        Long first = walkToSettle("SY-DONE", 0, amt("100"), amt("100")).getId();
        assertEquals(DONE, service.selectTHmfUseFlowById(first).getStatus());
        THmfUseFlow second = open("SY-DONE", 7);
        assertNotNull(second, "前一趟已办结，另起新张");
        assertFalse(first.equals(second.getId()));
    }

    // ---------- 公示天数守门 ----------

    @Test
    void advance_toPublishOpensDoor_daysZeroLetsClockInImmediately() {
        Long id = open("SY-2", 0).getId();
        assertEquals(PUBLISH, service.advance(id, 0, null).getStage());
        // 公示天数定 0：天数已走完，系统这一路可以自推进施工
        THmfUseFlow b = service.clockAdvance(id);
        assertNotNull(b);
        assertEquals(BUILD, b.getStage());
        assertEquals(SRC_CLOCK, tracks.all().get(tracks.all().size() - 1).getSrc(), "系统来路署名");
    }

    @Test
    void publishDaysNotElapsed_neitherClockNorTellerCanEnterBuild_evenMaterialsReady() {
        Long id = open("SY-3", 7).getId();
        service.advance(id, 0, null);
        // 材料递得再齐（合同先挂上），天数没走完，施工那扇门开着也不能进
        assertNotNull(service.attach(id, 1, null, null, null, null));
        assertNull(service.clockAdvance(id), "系统一路：天数没走完不推");
        assertNull(service.advance(id, PUBLISH, "柜员手快"), "柜员一路：同样进不去");
        assertEquals(PUBLISH, service.selectTHmfUseFlowById(id).getStage(), "停在公示不动");
        assertEquals(2, tracks.all().size(), "拒之门外，一道过档痕迹都不多落");
    }

    @Test
    void afterPublishDaysElapsed_bothRoutesEnterButOnlyOneTrackLands() {
        Long id = open("SY-4", 7).getId();
        service.advance(id, 0, null);
        // 系统手快：天数走满自推
        backdateEnter(id, PUBLISH, 7);
        assertNotNull(service.clockAdvance(id));
        assertEquals(BUILD, service.selectTHmfUseFlowById(id).getStage());
        // 柜员那一下才点到，屏上还以为在公示：意图与回放不齐，给回现状、不另起一行
        THmfUseFlow stale = service.advance(id, PUBLISH, "柜员补点");
        assertNotNull(stale, "第二路给回同一句话（现状），不是报错");
        assertEquals(BUILD, stale.getStage());
        assertEquals(1, forwardsOf(id, BUILD).size(), "两条来路只落一道施工痕迹");
        assertEquals(3, tracks.all().size());
    }

    @Test
    void tellerBeatsClock_clockAfterwardsGivesSameWordWithoutNewRow() {
        Long id = open("SY-5", 7).getId();
        service.advance(id, 0, null);
        backdateEnter(id, PUBLISH, 7);
        assertNotNull(service.advance(id, PUBLISH, null), "柜员手快先进");
        THmfUseFlow again = service.clockAdvance(id);
        assertNotNull(again, "系统后到给回现状，不报错");
        assertEquals(BUILD, again.getStage());
        assertEquals(1, forwardsOf(id, BUILD).size());
    }

    // ---------- 施工 / 结算门槛与列支 ----------

    @Test
    void buildStage_contractAloneNotEnoughToSettle() {
        Long id = atBuild("SY-6", 0);
        assertNotNull(service.attach(id, 1, null, null, null, null));
        assertNull(service.advance(id, BUILD, null), "只挂合同，验收监理没到，进不了结算");
        assertEquals(BUILD, service.selectTHmfUseFlowById(id).getStage());
    }

    @Test
    void settle_requiresContractAcceptanceSupervisorAccountAndAmount() {
        Long id = atBuild("SY-7", 0);
        Long acct = openAcct(amt("1000"));
        assertNotNull(service.attach(id, 1, "验收合格", "监理赵工", acct, null));
        assertNull(service.advance(id, BUILD, null), "列支额没给，进不去");
        assertNotNull(service.attach(id, null, null, null, null, amt("500")));
        THmfUseFlow done = service.advance(id, BUILD, null);
        assertNotNull(done);
        assertEquals(SETTLE, done.getStage());
        assertEquals(DONE, done.getStatus(), "走到结算才算办结一趟");
        assertEquals(0, amt("500").compareTo(accts.selectById(acct).getBalance()), "列支落地，从户面划走");
        assertTrack(tracks.all().get(tracks.all().size() - 1), SETTLE, 1, FWD, SRC_TELLER);
    }

    @Test
    void insufficientBalance_stopsOutsideSettleWholeAccountUntouched() {
        Long id = atBuild("SY-8", 0);
        Long acct = openAcct(amt("100"));
        assertNotNull(service.attach(id, 1, "验收合格", "监理赵工", acct, amt("500")));
        assertNull(service.advance(id, BUILD, null), "余额不够，停在结算门外");
        THmfUseFlow r = service.selectTHmfUseFlowById(id);
        assertEquals(BUILD, r.getStage());
        assertEquals(ACTIVE, r.getStatus(), "整张账不动");
        assertEquals(0, amt("100").compareTo(accts.selectById(acct).getBalance()), "户面一分没划");
        assertEquals(0, forwardsOf(id, SETTLE).size(), "结算痕迹也没有");
    }

    @Test
    void settledSheet_isSealed() {
        Long id = walkToSettle("SY-9", 0, amt("100"), amt("80")).getId();
        assertNull(service.advance(id, SETTLE, null));
        assertNull(service.rollback(id, SETTLE, null));
        assertNull(service.clockAdvance(id));
        assertNull(service.attach(id, 1, null, null, null, null));
        assertFalse(service.updateContent(id, "再记一笔"));
        assertFalse(service.remove(id), "办结的张也留档");
    }

    // ---------- 段次只由系统数痕迹 ----------

    @Test
    void stageColumnTamperedOnPaper_isIgnoredAndNoStageSkipped() {
        Long id = open("SY-10", 7).getId();
        service.advance(id, 0, null);
        // 纸面/库里被手改成"已到施工"，甚至直接拨到结算
        THmfUseFlow paper = service.selectTHmfUseFlowById(id);
        paper.setStage(SETTLE);
        flows.updateById(paper);
        assertNull(service.clockAdvance(id), "天数没走完，纸面上写结算也不认");
        // 天数走完，系统照痕迹数出在公示，只推到施工，绝不跳结算
        backdateEnter(id, PUBLISH, 7);
        assertEquals(BUILD, service.clockAdvance(id).getStage());
    }

    @Test
    void frontStageIntentBeyondReality_doesNotMoveSheet() {
        Long id = open("SY-11", 7).getId();
        THmfUseFlow r = service.advance(id, SETTLE, "界面乱给段号");
        assertNotNull(r);
        assertEquals(0, r.getStage(), "意图段号只当意图，当前在立项就不动");
        assertEquals(1, tracks.all().size());
    }

    @Test
    void sameStageSecondSubmit_keepsFirstWordNoNewLine() {
        Long id = open("SY-12", 7).getId();
        assertNotNull(service.advance(id, 0, null));
        // 同一档（立项）报第二遍：账上还是头一遍那句话
        THmfUseFlow again = service.advance(id, 0, "又点一下");
        assertNotNull(again);
        assertEquals(PUBLISH, again.getStage());
        assertEquals(1, forwardsOf(id, PUBLISH).size(), "不另起一行");
    }

    // ---------- 往后收回 ----------

    @Test
    void rollback_atInitiation_hasNowhereToGo() {
        Long id = open("SY-13", 7).getId();
        assertNull(service.rollback(id, 0, null));
        assertEquals(1, tracks.all().size());
    }

    @Test
    void rollback_fromPublish_sinksAndReopensNewWalkOldTracksKept() {
        Long id = open("SY-14", 7).getId();
        service.advance(id, 0, null);
        THmfUseFlow back = service.rollback(id, PUBLISH, "公示材料有误");
        assertEquals(0, back.getStage());
        assertEquals(2, back.getWalkNo(), "另起一趟");
        assertNull(back.getPublishStart(), "公示时刻沉底");
        assertTrack(tracks.all().get(tracks.all().size() - 1), PUBLISH, 1, BACK, SRC_TELLER);

        // 重走从立项重新攒：新趟一道前行，旧趟痕迹一行不抹
        THmfUseFlow re = service.advance(id, 0, null);
        assertEquals(PUBLISH, re.getStage());
        assertEquals(2, re.getWalkNo());
        assertEquals(1, forwardsOf(id, PUBLISH).stream().filter(t -> t.getWalkNo() == 1).count());
        assertEquals(1, forwardsOf(id, PUBLISH).stream().filter(t -> t.getWalkNo() == 2).count());
    }

    @Test
    void rollback_fromBuild_sinksContractMaterials_andRestartsPublishClock() {
        Long id = atBuild("SY-15", 7);
        assertNotNull(service.attach(id, 1, "验收合格", "监理赵工", openAcct(amt("100")), amt("50")));
        THmfUseFlow back = service.rollback(id, BUILD, "合同要重挂");
        assertEquals(PUBLISH, back.getStage());
        assertEquals(2, back.getWalkNo());
        assertEquals(0, back.getContractReady(), "施工档挂的事沉底");
        assertNull(back.getAcceptRecord());
        assertNull(back.getSupervisorName());
        assertNull(back.getAcctId());
        assertNull(back.getPayAmt());
        // 公示从上一趟退回那一刻重新计时：此刻天数（7）没走完，施工门不开
        assertNull(service.clockAdvance(id));
        // 把退回时刻拨回 7 天前，门才开
        backdateLastBack(id);
        assertEquals(BUILD, service.clockAdvance(id).getStage());
        assertEquals(1, forwardsOf(id, BUILD).stream().filter(t -> t.getWalkNo() == 2).count(), "新趟重新进施工");
        assertEquals(1, forwardsOf(id, BUILD).stream().filter(t -> t.getWalkNo() == 1).count(), "旧趟那道还在");
    }

    @Test
    void rollback_staleIntentGivesCurrentWordNoBackRow() {
        Long id = atBuild("SY-16", 0);
        THmfUseFlow r = service.rollback(id, PUBLISH, "屏上还显示公示");
        assertNotNull(r);
        assertEquals(BUILD, r.getStage(), "意图与回放不齐，给回现状，不重复收");
        assertEquals(0, tracks.all().stream().filter(t -> t.getAction() == BACK).count());
    }

    // ---------- 作废 ----------

    @Test
    void cancel_freezesSheet_wordsCannotChange_sheetCannotDelete() {
        Long id = open("SY-17", 7).getId();
        service.advance(id, 0, null);
        THmfUseFlow v = service.cancel(id, "业主异议");
        assertEquals(VOID, v.getStatus());
        assertEquals(PUBLISH, v.getStage(), "作废时停在哪档就记在哪档");
        assertNull(service.advance(id, PUBLISH, null), "作废旧走不动档");
        assertNull(service.rollback(id, PUBLISH, null));
        assertNull(service.cancel(id, null), "不能再废第二回");
        assertNull(service.attach(id, 1, null, null, null, null), "续不动记事/材料");
        assertFalse(service.updateContent(id, "改个字"), "字改不得");
        assertFalse(service.remove(id), "张删不得");
        assertEquals(1, tracks.all().stream().filter(t -> t.getAction() == CANCEL).count());
    }

    @Test
    void remove_activeSheetAllowed_voidAndDoneNot() {
        Long a = open("SY-18a", 7).getId();
        assertTrue(service.remove(a), "在办张可删");
        Long b = open("SY-18b", 7).getId();
        service.cancel(b, null);
        assertFalse(service.remove(b));
    }

    // ---------- 走完那趟另有的一笔账 ----------

    @Test
    void verifyWalk_cleanWalkThroughSettle_passes() {
        Long id = walkToSettle("SY-19", 0, amt("200"), amt("150")).getId();
        assertTrue(service.verifyWalk(id));
    }

    @Test
    void verifyWalk_rollbackThenRewalkToSettle_passes() {
        Long id = atBuild("SY-20", 0);
        assertNotNull(service.rollback(id, BUILD, null));
        service.clockAdvance(id);
        Long acct = openAcct(amt("300"));
        service.attach(id, 1, "复验合格", "监理钱工", acct, amt("200"));
        assertEquals(SETTLE, service.advance(id, BUILD, null).getStage());
        assertTrue(service.verifyWalk(id), "退回重走后严丝合缝到结算，也算走通");
    }

    @Test
    void verifyWalk_extraRecord_fails() {
        Long id = walkToSettle("SY-21", 0, amt("100"), amt("10")).getId();
        // 多出一条记载：同一趟同一档又塞一道前行
        THmfUseTrack extra = new THmfUseTrack();
        extra.setFlowId(id);
        extra.setStageNo(BUILD);
        extra.setWalkNo(1);
        extra.setAction(FWD);
        extra.setSrc(SRC_TELLER);
        extra.setOperator("外人");
        extra.setActTime(new Date());
        extra.setDelFlag(0);
        tracks.insert(extra);
        assertFalse(service.verifyWalk(id));
    }

    @Test
    void verifyWalk_missingStage_fails() {
        Long id = walkToSettle("SY-22", 0, amt("100"), amt("10")).getId();
        // 把施工那道痕迹抹改成结算：缺一档
        forwardsOf(id, BUILD).get(0).setStageNo(SETTLE);
        assertFalse(service.verifyWalk(id));
    }

    @Test
    void verifyWalk_backfilledAfterwards_fails() {
        Long id = walkToSettle("SY-23", 0, amt("100"), amt("10")).getId();
        // 事后补进去的：落纸次序在最后，落笔时刻却写在很久以前
        THmfUseTrack fake = new THmfUseTrack();
        fake.setFlowId(id);
        fake.setStageNo(PUBLISH);
        fake.setWalkNo(1);
        fake.setAction(FWD);
        fake.setSrc(SRC_TELLER);
        fake.setOperator("补痕的人");
        fake.setActTime(new Date(System.currentTimeMillis() - 30L * DAY_MS));
        fake.setDelFlag(0);
        tracks.insert(fake);
        assertFalse(service.verifyWalk(id));
    }

    @Test
    void verifyWalk_voidRowOrNotDone_fails() {
        assertFalse(service.verifyWalk(404L));
        Long id = open("SY-24", 7).getId();
        assertFalse(service.verifyWalk(id), "没办结不算走通");
        service.cancel(id, null);
        assertFalse(service.verifyWalk(id));
    }

    @Test
    void trackTrail_listsSettleBackToInitiation_newWalkFirst() {
        Long id = atBuild("SY-25", 0);
        service.rollback(id, BUILD, null);
        List<THmfUseTrack> trail = service.listTrackTrail(id);
        assertNotNull(trail);
        for (int i = 1; i < trail.size(); i++) {
            int w0 = trail.get(i - 1).getWalkNo();
            int w1 = trail.get(i).getWalkNo();
            assertTrue(w0 > w1 || (w0 == w1 && trail.get(i - 1).getStageNo() >= trail.get(i).getStageNo()),
                    "倒序：新趟在前、同趟档号倒着排");
        }
        assertNull(service.listTrackTrail(404L));
    }

    // ---------- helpers ----------

    private THmfUseFlow open(String bizNo, int days) {
        return service.open(bizNo, "屋面（3号楼顶层）", amt("128000.00"), "顺达建工", 1, days, "屋面防水");
    }

    private Long openAcct(BigDecimal balance) {
        THmfAcctCard a = new THmfAcctCard();
        a.setBillNo("FH-" + System.nanoTime());
        a.setSiteNo("JZ00");
        a.setBalance(balance);
        a.setDelFlag(0);
        accts.insert(a);
        return a.getId();
    }

    /** 立项→公示→施工（公示天数定 0 或把进公示时刻拨回）。 */
    private Long atBuild(String bizNo, int days) {
        Long id = open(bizNo, days).getId();
        service.advance(id, 0, null);
        if (days > 0) {
            backdateEnter(id, PUBLISH, days);
        }
        THmfUseFlow b = service.clockAdvance(id);
        assertNotNull(b);
        assertEquals(BUILD, b.getStage());
        return id;
    }

    private THmfUseFlow walkToSettle(String bizNo, int days, BigDecimal balance, BigDecimal pay) {
        Long id = atBuild(bizNo, days);
        Long acct = openAcct(balance);
        THmfUseFlow attached = service.attach(id, 1, "验收合格", "监理赵工", acct, pay);
        assertNotNull(attached);
        THmfUseFlow done = service.advance(id, BUILD, null);
        assertNotNull(done);
        return done;
    }

    private List<THmfUseTrack> forwardsOf(Long id, int stage) {
        return tracks.all().stream()
                .filter(t -> id.equals(t.getFlowId()))
                .filter(t -> t.getStageNo() == stage)
                .filter(t -> t.getAction() == FWD)
                .collect(Collectors.toList());
    }

    /** 把本档"往前进入"那道痕迹的时刻拨回 days 天前（模拟公示天数走完）。 */
    private void backdateEnter(Long id, int stage, int days) {
        tracks.all().stream()
                .filter(t -> id.equals(t.getFlowId()))
                .filter(t -> t.getStageNo() == stage && t.getAction() == FWD)
                .reduce((a, b) -> b)
                .ifPresent(t -> t.setActTime(new Date(System.currentTimeMillis() - days * DAY_MS)));
    }

    /** 把最近一道收回痕迹的时刻拨回 7 天前（模拟从上一趟退回后公示又走满）。 */
    private void backdateLastBack(Long id) {
        tracks.all().stream()
                .filter(t -> id.equals(t.getFlowId()))
                .filter(t -> t.getAction() == BACK)
                .reduce((a, b) -> b)
                .ifPresent(t -> t.setActTime(new Date(System.currentTimeMillis() - 7 * DAY_MS)));
    }

    private void assertTrack(THmfUseTrack t, int stage, int walk, int action, int src) {
        assertEquals(stage, t.getStageNo());
        assertEquals(walk, t.getWalkNo());
        assertEquals(action, t.getAction());
        assertEquals(src, t.getSrc());
        assertNotNull(t.getActTime());
        assertNotNull(t.getOperator());
    }

    private static BigDecimal amt(String v) {
        return new BigDecimal(v);
    }
}
