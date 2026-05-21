package com.youmo.core.service.impl;

import com.youmo.common.entity.WorldSetting;
import com.youmo.core.repository.WorldSettingRepository;
import com.youmo.core.service.WorldSettingService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorldSettingServiceImpl implements WorldSettingService {

    private final WorldSettingRepository worldSettingRepository;

    @Override
    public Optional<WorldSetting> getByBookId(Long bookId) {
        return worldSettingRepository.findByBookId(bookId);
    }

    @Override
    @Transactional
    public WorldSetting saveOrUpdate(Long bookId, WorldSetting setting) {
        Optional<WorldSetting> existing = worldSettingRepository.findByBookId(bookId);
        if (existing.isPresent()) {
            WorldSetting ws = existing.get();
            ws.setEra(setting.getEra());
            ws.setGeography(setting.getGeography());
            ws.setHistoryEvents(setting.getHistoryEvents());
            ws.setPolitics(setting.getPolitics());
            ws.setEconomy(setting.getEconomy());
            ws.setCulture(setting.getCulture());
            ws.setMilitary(setting.getMilitary());
            ws.setCoreRuleType(setting.getCoreRuleType());
            ws.setCoreRuleSummary(setting.getCoreRuleSummary());
            ws.setExtraAttributes(setting.getExtraAttributes());
            return worldSettingRepository.save(ws);
        }
        setting.setBook(null); // 由调用方设置
        return worldSettingRepository.save(setting);
    }
}
