package com.youmo.core.service;

import java.util.List;

public interface ConsistencyCheckService {

    record ConsistencyIssue(String entity, String description, String severity, String type) {}

    record ConsistencyReport(
        List<ConsistencyIssue> characterIssues,
        List<ConsistencyIssue> timelineIssues,
        List<ConsistencyIssue> worldIssues,
        List<ConsistencyIssue> foreshadowingIssues,
        List<ConsistencyIssue> toneIssues
    ) {
        public List<ConsistencyIssue> allIssues() {
            List<ConsistencyIssue> all = new java.util.ArrayList<>();
            if (characterIssues != null) all.addAll(characterIssues);
            if (timelineIssues != null) all.addAll(timelineIssues);
            if (worldIssues != null) all.addAll(worldIssues);
            if (foreshadowingIssues != null) all.addAll(foreshadowingIssues);
            if (toneIssues != null) all.addAll(toneIssues);
            return all;
        }
    }

    ConsistencyReport checkAll(String beforeContext, String newContent);
}
