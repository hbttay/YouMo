// Mock AI API responses — all non-streaming endpoints
// Format matches what the frontend expects after fetch().json()
// For SSE endpoints, see mock-setup.js createSSEMock()

export const MOCK = {
  // ── Random book idea ──
  randomBookIdea: {
    success: true,
    data: {
      title: '星辰陨落之时',
      core_idea: '在科技与魔法并存的世界，一位被流放的公主发现了隐藏在星辰背后的秘密，踏上夺回王座的旅程。',
      creation_mode: 'LINEAR',
      target_length: 'MEDIUM',
    },
  },

  // ── Random character ──
  randomCharacter: {
    success: true,
    data: {
      name: '云汐',
      gender: '女',
      age_description: '约莫十七八岁',
      appearance: '一袭白衣，长发如瀑，眉间一点朱砂',
      origin: '青云门内门弟子',
      identity: '青云门掌门之女',
      depth_level: 'L3',
      race: '人族',
      extra_attributes: '{}',
    },
  },

  // ── Character fission ──
  characterFission: {
    success: true,
    data: {
      name: '云汐（暗裔）',
      gender: '女',
      age_description: '外表十七八岁，实际三百岁',
      appearance: '黑发紫瞳，左脸有暗纹',
      origin: '深渊裂隙',
      identity: '暗裔王族末裔',
      depth_level: 'L2',
    },
  },

  // ── Random world setting ──
  randomWorldSetting: {
    success: true,
    data: {
      era: '上古纪元',
      geography: '大陆分为三块：东胜神州、西牛贺洲、南赡部洲',
      history_events: '【太古之战】神魔大战，天地崩裂\n【封神纪元】诸神设立封印',
      politics: '三洲各自为政，以宗门为权力核心',
      economy: '以灵石为通用货币，炼丹炼器为主要产业',
      culture: '崇尚强者为尊，宗门内部等级森严',
      military: '各大宗门均有护山大阵和执法堂',
      core_rule_type: '灵气体系',
      core_rule_summary: '天地灵气分为金木水火土五行，修炼者吸收灵气凝聚金丹',
    },
  },

  // ── Random outline ──
  randomOutline: {
    success: true,
    data: {
      volumes: [
        {
          title: '第一卷：流放的公主',
          chapters: [
            { title: '第一章：星夜逃亡', scenes: [{ title: '出城' }, { title: '追兵' }] },
            { title: '第二章：边境小镇', scenes: [{ title: '落脚' }] },
          ],
        },
        {
          title: '第二卷：暗流涌动',
          chapters: [
            { title: '第三章：阴谋初现', scenes: [{ title: '密信' }] },
          ],
        },
      ],
    },
  },

  // ── Random outline expand ──
  randomOutlineExpand: {
    success: true,
    data: {
      volumes: [{ title: '一卷', chapters: [{ title: '第一章', scenes: [{ title: '第一节' }] }] }],
    },
  },

  // ── Optimize instructions ──
  optimizeInstructions: {
    success: true,
    data: {
      optimized: '续写下一段：主角推开石门，发现密室中摆放着一具石棺。请着重描写主角的心理活动，营造悬疑氛围。避免直接揭示石棺的秘密。',
    },
  },

  // ── Continue plan ──
  continuePlan: {
    success: true,
    data: {
      plan: '1. 主角推开石门进入密室\n2. 发现石棺上的铭文\n3. 听到身后有脚步声\n4. 转身发现神秘人物',
      reasoning: '当前章节需要推进主线剧情，石棺铭文是后续伏笔的关键线索。',
    },
  },

  // ── Analyze chapter ──
  analyzeChapter: {
    success: true,
    data: {
      core_events: '主角推开石门，发现密室中的石棺',
      characters_appeared: ['主角云汐', '神秘人'],
      character_state_changes: [{ name: '云汐', from: '平静', to: '紧张' }],
      emotion_curve: [{ position: '开头', emotion: '好奇' }, { position: '中间', emotion: '紧张' }],
      new_foreshadowings: ['石棺铭文暗示上古秘密'],
      world_elements: ['密室阵法', '石棺'],
    },
  },

  // ── Writing guide ──
  writingGuide: {
    success: true,
    data: {
      suggestions: [
        { dimension: '节奏', severity: 'medium', content: '密室场景可以放慢节奏，增加细节描写', position: '开头' },
        { dimension: '对话', severity: 'low', content: '主角独白较多，可增加内心活动的层次感', position: null },
        { dimension: '悬念', severity: 'high', content: '石棺的揭示节奏可以再拉长，先铺垫声音/气味', position: '中间' },
      ],
    },
  },

  // ── Consistency check ──
  consistencyCheck: {
    success: true,
    data: {
      character_issues: [
        {
          entity: '云汐',
          description: '前文中云汐是"黑发"，本章中写为"银发"',
          type: 'character',
          severity: 'medium',
          suggestion: '修改为"一头墨发在月光下闪烁"',
        },
      ],
      timeline_issues: [
        {
          entity: '时间线',
          description: '前文时间是「午夜子时」，本章写到"夕阳西下"',
          type: 'timeline',
          severity: 'high',
          suggestion: '修改为"月色朦胧，夜色渐深"',
        },
      ],
      world_issues: [
        {
          entity: '灵石',
          description: '前文设定灵石为稀有品，本章随手拿出五块',
          type: 'world',
          severity: 'low',
          suggestion: '修改为"她从怀中掏出一块灵石"',
        },
      ],
      foreshadowing_issues: [
        {
          entity: '石棺伏笔',
          description: '第三章埋下"石棺与主角血脉相关"的伏笔未呼应',
          type: 'foreshadowing',
          severity: 'medium',
          suggestion: '可在此处加入主角看到石棺铭文的血脉反应',
        },
      ],
      tone_issues: [
        {
          entity: '套路化表达',
          description: '本章出现「一股莫名的力量」——AI 套路化表达',
          type: 'tone',
          severity: 'high',
          suggestion: '修改为"无形之力将她推开，仿佛密室本身在拒绝她的靠近"',
        },
      ],
    },
  },

  // ── Author suggestions ──
  suggestions: {
    success: true,
    data: [
      {
        id: 's1',
        type: 'rewrite',
        position: { start: 0, end: 50 },
        original: '主角推开了密室的门',
        suggestion: '主角的手指触碰到石门的瞬间，一股寒意从指尖蔓延至全身。她顿了顿，运起灵力护住心脉，这才用力推开了密室的门。',
        reason: '增加感官细节，让场景更有沉浸感',
      },
      {
        id: 's2',
        type: 'consistency',
        position: { start: 100, end: 120 },
        original: '密室中摆放着一具石棺',
        suggestion: '密室中央，一具青石棺椁静静安放，棺盖上的铭文在黑暗中微微发光。',
        reason: '丰富场景描写，呼应后文的古文解读',
      },
    ],
  },

  // ── Chat character (non-streaming) ──
  chatCharacter: {
    success: true,
    data: {
      reply: '（云汐抬起头，目光坚定）我是青云门掌门之女，无论前路有多艰难，我绝不会退缩。',
    },
  },

  // ── Book report ──
  bookReport: {
    success: true,
    data: {
      overall_assessment: '整体写作进展良好，角色塑造生动',
      strengths: ['世界观设定丰富', '角色性格鲜明'],
      weaknesses: ['部分章节节奏偏快', '对话可更自然'],
      suggestions: ['适当增加场景描写', '注意伏笔的回收节奏'],
    },
  },

  // ── Foreshadowing scan ──
  scanForeshadowings: {
    success: true,
    data: [
      {
        description: '石棺铭文暗示的上古秘密（第三章提及）',
        type: 'EVENT',
        importance: 'HIGH',
        source_chapter: '第三章：密室',
        source_text: '铭文刻着古老的符文，闪烁着幽蓝色的光芒',
      },
      {
        description: '神秘人暗中跟随主角（第二章暗示）',
        type: 'CHARACTER',
        importance: 'MEDIUM',
        source_chapter: '第二章：边境小镇',
        source_text: '街角的黑影一闪而过',
      },
    ],
  },

  // ── Feedback analysis ──
  analyzeFeedback: {
    success: true,
    data: {
      analysis: '该反馈属于功能建议类，建议升级为技术需求评估。',
      category: '功能建议',
      severity: 'MEDIUM',
    },
  },
}
