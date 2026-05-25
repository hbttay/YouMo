export const STATUS_LABEL = {
  DRAFT: '草稿',
  SERIALIZING: '连载中',
  COMPLETED: '已完结',
  ARCHIVED: '已归档',
}

export const CREATION_LABEL = {
  LINEAR: '线性叙事',
  DIVERGENT: '分支叙事',
}

const _CREATION_REVERSE = {}
for (const [k, v] of Object.entries(CREATION_LABEL)) _CREATION_REVERSE[v] = k
export const CREATION_KEY = _CREATION_REVERSE

export const LENGTH_LABEL = {
  SHORT: '短篇',
  MEDIUM: '中篇',
  LONG: '长篇',
}

const _LENGTH_REVERSE = {}
for (const [k, v] of Object.entries(LENGTH_LABEL)) _LENGTH_REVERSE[v] = k
export const LENGTH_KEY = _LENGTH_REVERSE

export const NODE_TYPE = {
  VOLUME: '卷',
  CHAPTER: '章',
  SCENE: '节',
}

export const NODE_COLOR = {
  VOLUME: '#6d28d9',
  CHAPTER: '#2563eb',
  SCENE: '#9ca3af',
}
