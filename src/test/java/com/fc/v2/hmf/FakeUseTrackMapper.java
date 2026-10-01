package com.fc.v2.hmf;

import java.io.Serializable;

import com.fc.v2.mapper.auto.THmfUseTrackMapper;
import com.fc.v2.model.auto.THmfUseTrack;

/** 过档痕迹流水内存假 Mapper。 */
class FakeUseTrackMapper extends InMemoryMapper<THmfUseTrack> implements THmfUseTrackMapper {

    @Override
    Serializable idOf(THmfUseTrack t) {
        return t.getId();
    }

    @Override
    void assignId(THmfUseTrack t, long id) {
        t.setId(id);
    }
}
