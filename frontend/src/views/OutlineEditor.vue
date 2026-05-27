<template>
  <div class="outline-editor">
    <!-- ========== Back ========== -->
    <router-link v-if="route.query.from === 'hub'" to="/modules/outline" class="back-link">&larr; 返回大纲工坊</router-link>
    <router-link v-else :to="`/books/${bookId}`" class="back-link">&larr; 返回书籍详情</router-link>

    <!-- ========== Header ========== -->
    <div class="editor-header">
      <h1>大纲编排</h1>
      <div class="header-actions">
        <button v-if="tree.length > 0" class="btn btn-sm btn-outline" @click="expandAll">
          {{ allExpanded ? '折叠全部' : '展开全部' }}
        </button>
        <div v-if="tree.length > 0" class="view-mode-group">
          <button class="btn btn-sm" :class="{ 'btn-outline': viewMode !== 'tree', 'btn-primary': viewMode === 'tree' }" @click="viewMode = 'tree'">树形</button>
          <button class="btn btn-sm" :class="{ 'btn-outline': viewMode !== 'mindmap', 'btn-primary': viewMode === 'mindmap' }" @click="viewMode = 'mindmap'">导图</button>
          <button class="btn btn-sm" :class="{ 'btn-outline': viewMode !== 'table', 'btn-primary': viewMode === 'table' }" @click="viewMode = 'table'">列表</button>
        </div>
        <DraftsDrawer :book-id="bookId" type="outline" @apply="handleDraftApply" />
        <button class="btn btn-random" :disabled="outlineGenerating" @click="handleRandomOutline">
          {{ outlineGenerating ? '生成中...' : '随机大纲' }}
        </button>
        <button class="btn btn-primary" @click="openCreateForm(null)">+ 新建卷</button>
      </div>
    </div>

    <!-- ========== 总纲 ========== -->
    <div class="synopsis-section">
      <div class="synopsis-header">
        <label class="synopsis-label">总纲</label>
        <span class="synopsis-hint">全书大纲概要，决定故事走向与结构</span>
      </div>
      <textarea
        v-model="synopsis"
        class="synopsis-textarea"
        rows="5"
        placeholder="在此规划全书结构思路……例如：故事分为几个阶段、各卷承担什么功能等。"
        @blur="saveSynopsis"
      ></textarea>
      <div class="synopsis-actions">
        <span v-if="synopsisSaved" class="synopsis-saved">已保存</span>
        <button
          class="btn btn-sm btn-save-synopsis"
          :disabled="synopsisSaving"
          @click="saveSynopsis"
        >
          {{ synopsisSaving ? '保存中...' : '保存总纲' }}
        </button>
      </div>
    </div>

    <!-- ========== Order warning ========== -->
    <div v-if="statusWarning" class="status-warning">{{ statusWarning }}
      <router-link to="world-setting" class="status-link">去设置 →</router-link>
    </div>

    <!-- ========== Messages ========== -->
    <Transition name="msg">
      <div v-if="errorMsg" class="msg-bar msg-error">{{ errorMsg }}</div>
    </Transition>
    <Transition name="msg">
      <div v-if="successMsg" class="msg-bar msg-success">{{ successMsg }}</div>
    </Transition>

    <!-- ========== Create Form ========== -->
    <form v-if="showCreateForm" class="create-form" @submit.prevent="handleCreate">
      <div class="create-form-header">
        <h3 v-if="createForm.parent_id">
          新增{{ getTypeLabel(createForm.node_type) }}到「{{ createParentTitle }}」
        </h3>
        <h3 v-else>新建卷</h3>
      </div>
      <div class="create-form-body">
        <div class="form-field">
          <label>标题 <span class="required">*</span></label>
          <input v-model="createForm.title" type="text" :placeholder="'输入' + getTypeLabel(createForm.node_type) + '名称'" />
        </div>
        <div class="form-field">
          <label>类型</label>
          <select v-model="createForm.node_type">
            <option value="VOLUME">卷</option>
            <option value="CHAPTER">章</option>
            <option value="SCENE">节</option>
          </select>
        </div>
        <div class="form-actions">
          <button type="submit" class="btn btn-primary" :disabled="createLoading">
            {{ createLoading ? '创建中...' : '创建' }}
          </button>
          <button type="button" class="btn btn-cancel" @click="cancelCreateForm">取消</button>
        </div>
      </div>
    </form>

    <!-- ========== Loading ========== -->
    <LoadingSpinner v-if="fetchLoading" />

    <!-- ========== Empty State ========== -->
    <div v-else-if="tree.length === 0" class="empty-state">
      <div class="empty-card">
        <h2>开始编排大纲</h2>
        <div class="guide-steps">
          <div class="guide-step">
            <span class="guide-icon">📋</span>
            <div>
              <strong>第一步：填写「总纲」</strong>
              <p>在上方总纲区规划全书结构思路，明确故事走向</p>
            </div>
          </div>
          <div class="guide-step">
            <span class="guide-icon">📖</span>
            <div>
              <strong>第二步：创建「卷」</strong>
              <p>卷是最大结构单元（如"第一卷"、"上卷"）</p>
            </div>
          </div>
          <div class="guide-step">
            <span class="guide-icon">📄</span>
            <div>
              <strong>第三步：在卷下创建「章」</strong>
              <p>按章节组织故事发展，小说以章为基本单位</p>
            </div>
          </div>
          <div class="guide-step guide-step-optional">
            <span class="guide-icon">📝</span>
            <div>
              <strong>可选：在章下创建「节」</strong>
              <p>需要精细规划时，可将一章拆分为多个场景节点</p>
            </div>
          </div>
        </div>
        <button class="btn btn-primary btn-lg" @click="openCreateForm(null)">+ 创建第一卷</button>
        <button class="btn btn-random btn-lg" style="margin-top:12px" :disabled="outlineGenerating" @click="handleRandomOutline">
          {{ outlineGenerating ? '生成中...' : '随机生成完整大纲' }}
        </button>
        <button
          class="btn btn-secondary btn-lg"
          style="margin-top:8px"
          :disabled="outlineGenerating || !oneSentence"
          :title="!oneSentence ? '请先在书籍详情页填写一句话梗概' : '根据一句话梗概扩写大纲'"
          @click="handleOutlineExpand"
        >
          {{ outlineGenerating ? '生成中...' : '一句话生成大纲' }}
        </button>
      </div>
    </div>

    <!-- ========== Mind Map View ========== -->
    <template v-else-if="viewMode === 'mindmap'">
      <MindMapView :tree="tree" :book-id="bookId" @navigate="handleMindMapNavigate" />
    </template>

    <!-- ========== Tree ========== -->
    <template v-else-if="viewMode === 'tree'">
    <div class="tree-container" @dragover.prevent>
      <div v-for="node in tree" :key="node.id" class="tree-node">
        <!-- ── Volume / Root Node ── -->
        <div
          class="node-row node-volume"
          :class="[dragOverClass(node), { 'is-editing': editingId === node.id, collapsed: collapsedNodes.has(node.id), 'is-dragging': dragNode?.id === node.id, 'is-focused': lastFocusedId === node.id }]"
          @click="canHaveChildren(node.node_type) && toggleCollapse(node.id)"
          draggable="true"
          @dragstart="handleDragStart(node)"
          @dragover="handleDragOver($event, node)"
          @dragleave="handleDragLeave"
          @drop="handleDrop($event, node)"
          @dragend="handleDragEnd"
        >
          <div class="color-bar" :style="{ background: getTypeColor(node.node_type) }"></div>

          <template v-if="editingId === node.id">
            <div class="edit-inline">
              <input v-model="editForm.title" type="text" placeholder="标题" />
              <select v-model="editForm.node_type">
                <option value="VOLUME">卷</option>
                <option value="CHAPTER">章</option>
                <option value="SCENE">节</option>
              </select>
              <button class="btn btn-sm btn-save" :disabled="updateLoading" @click="handleUpdate(node.id)">保存</button>
              <button class="btn btn-sm btn-cancel-sm" @click="cancelEdit">取消</button>
            </div>
          </template>

          <template v-else>
            <div class="node-body">
              <span class="fold-indicator">{{ collapsedNodes.has(node.id) ? '▸' : '▾' }}</span>
              <span class="type-badge" :style="{ background: getTypeColor(node.node_type) + '18', color: getTypeColor(node.node_type) }">
                {{ getTypeLabel(node.node_type) }}
              </span>
              <span class="node-title">{{ node.title }}</span>
              <span v-if="(node.node_type === 'CHAPTER' || node.node_type === 'SCENE') && node.word_count" class="word-badge">{{ node.word_count }} 字</span>
              <span v-if="node.node_type === 'CHAPTER' || node.node_type === 'SCENE'" class="status-dot clickable" :style="{ background: getStatusColor(node.status) }" :title="getStatusLabel(node.status) + ' — 点击切换'" @click.stop="cycleStatus(node)"></span>
              <template v-if="node.node_type === 'VOLUME' && volumeChapterProgress(node)">
                <span class="volume-progress-bar"><span class="volume-progress-fill" :style="{ width: volumeChapterProgress(node).pct + '%' }"></span></span>
                <span class="volume-progress-text">{{ volumeChapterProgress(node).done }}/{{ volumeChapterProgress(node).total }} 章</span>
                <span v-if="volumeTotalWords(node)" class="volume-words">{{ volumeTotalWords(node) }} 字</span>
              </template>
              <!-- Synopsis toggle for VOLUME -->
              <button
                v-if="node.node_type === 'VOLUME'"
                class="note-toggle volume"
                :class="{ 'has-content': node.writing_goal }"
                title="卷概要"
                @click.stop="toggleGoal(node)"
              >
                概要{{ node.writing_goal ? ' ·' : '' }}
              </button>
            </div>
            <div class="node-actions" @click.stop>
              <button v-if="canHaveChildren(node.node_type)" class="btn-action" :title="'添加' + childLabel(node.node_type)" @click="openCreateForm(node.id)">
                + {{ childLabel(node.node_type) }}
              </button>
              <button class="btn-action" title="编辑" @click="startEdit(node)">编辑</button>
              <button class="btn-action" :title="pinnedNodes.has(node.id) ? '取消置顶' : '置顶'" @click="togglePin(node)">
                {{ pinnedNodes.has(node.id) ? '取消' : '置顶' }}
              </button>
              <button class="btn-action btn-action-danger" title="删除" @click="confirmDelete(node.id, node.title, node.node_type)">删除</button>
            </div>
          </template>
        </div>

        <!-- Volume Synopsis (expandable) -->
        <div v-if="node.node_type === 'VOLUME' && goalOpen.has(node.id)" class="goal-panel volume">
          <textarea
            v-model="goalCache[node.id]"
            class="goal-textarea volume"
            rows="3"
            :placeholder="'为「' + node.title + '」撰写概要……'"
            @blur="saveGoal(node.id)"
          ></textarea>
        </div>

        <!-- ── Children ── -->
        <div v-if="!collapsedNodes.has(node.id) && node.children && node.children.length" class="children">
          <div v-for="child in node.children" :key="child.id" class="tree-node">
            <div
              class="node-row node-chapter"
              :class="[dragOverClass(child), { 'is-editing': editingId === child.id, collapsed: collapsedNodes.has(child.id), 'is-dragging': dragNode?.id === child.id, 'is-focused': lastFocusedId === child.id }]"
              @click="canHaveChildren(child.node_type) && toggleCollapse(child.id)"
              draggable="true"
              @dragstart="handleDragStart(child)"
              @dragover="handleDragOver($event, child)"
              @dragleave="handleDragLeave"
              @drop="handleDrop($event, child)"
              @dragend="handleDragEnd"
            >
              <div class="color-bar" :style="{ background: getTypeColor(child.node_type) }"></div>

              <template v-if="editingId === child.id">
                <div class="edit-inline">
                  <input v-model="editForm.title" type="text" placeholder="标题" />
                  <select v-model="editForm.node_type">
                    <option value="VOLUME">卷</option>
                    <option value="CHAPTER">章</option>
                    <option value="SCENE">节</option>
                  </select>
                      <button class="btn btn-sm btn-save" :disabled="updateLoading" @click="handleUpdate(child.id)">保存</button>
                  <button class="btn btn-sm btn-cancel-sm" @click="cancelEdit">取消</button>
                </div>
              </template>

              <template v-else>
                <div class="node-body">
                  <span class="fold-indicator">{{ collapsedNodes.has(child.id) ? '▸' : '▾' }}</span>
                  <span class="type-badge" :style="{ background: getTypeColor(child.node_type) + '18', color: getTypeColor(child.node_type) }">
                    {{ getTypeLabel(child.node_type) }}
                  </span>
                  <span class="node-title">{{ child.title }}</span>
                  <span v-if="(child.node_type === 'CHAPTER' || child.node_type === 'SCENE') && child.word_count" class="word-badge">{{ child.word_count }} 字</span>
                  <span v-if="child.node_type === 'CHAPTER' || child.node_type === 'SCENE'" class="status-dot clickable" :style="{ background: getStatusColor(child.status) }" :title="getStatusLabel(child.status) + ' — 点击切换'" @click.stop="cycleStatus(child)"></span>
                      <!-- Notes toggle for CHAPTER -->
                  <button
                    v-if="child.node_type === 'CHAPTER'"
                    class="note-toggle chapter"
                    :class="{ 'has-content': child.writing_goal }"
                    title="章备注"
                    @click.stop="toggleGoal(child)"
                  >
                    备注{{ child.writing_goal ? ' ·' : '' }}
                  </button>
                </div>
                <div class="node-actions" @click.stop>
                  <router-link
                    v-if="child.node_type === 'CHAPTER' || child.node_type === 'SCENE'"
                    :to="`/books/${bookId}/write/${child.id}?title=${encodeURIComponent(child.title)}&node_type=${child.node_type}`"
                    class="btn-action btn-write"
                    title="写正文"
                    @click.stop="setFocused(child)"
                  >
                    写正文
                  </router-link>
                  <button v-if="canHaveChildren(child.node_type)" class="btn-action" :title="'添加' + childLabel(child.node_type)" @click="openCreateForm(child.id)">
                    + {{ childLabel(child.node_type) }}
                  </button>
                  <button class="btn-action" title="编辑" @click="startEdit(child)">编辑</button>
                  <button class="btn-action" :title="pinnedNodes.has(child.id) ? '取消置顶' : '置顶'" @click="togglePin(child)">
                    {{ pinnedNodes.has(child.id) ? '取消' : '置顶' }}
                  </button>
                  <button class="btn-action btn-action-danger" title="删除" @click="confirmDelete(child.id, child.title, child.node_type)">删除</button>
                </div>
              </template>
            </div>

            <!-- Chapter Notes (expandable) -->
            <div v-if="child.node_type === 'CHAPTER' && goalOpen.has(child.id)" class="goal-panel chapter">
              <textarea
                v-model="goalCache[child.id]"
                class="goal-textarea chapter"
                rows="3"
                :placeholder="'为「' + child.title + '」撰写备注……'"
                @blur="saveGoal(child.id)"
              ></textarea>
            </div>

            <!-- ── Grandchildren ── -->
            <div v-if="!collapsedNodes.has(child.id) && child.children && child.children.length" class="children">
              <div v-for="grand in child.children" :key="grand.id" class="tree-node">
                <div
                  class="node-row"
                  :class="[dragOverClass(grand), { 'is-editing': editingId === grand.id, 'is-dragging': dragNode?.id === grand.id }]"
                  draggable="true"
                  @dragstart="handleDragStart(grand)"
                  @dragover="handleDragOver($event, grand)"
                  @dragleave="handleDragLeave"
                  @drop="handleDrop($event, grand)"
                  @dragend="handleDragEnd"
                >
                  <div class="color-bar" :style="{ background: getTypeColor(grand.node_type) }"></div>

                  <template v-if="editingId === grand.id">
                    <div class="edit-inline">
                      <input v-model="editForm.title" type="text" placeholder="标题" />
                      <select v-model="editForm.node_type">
                        <option value="VOLUME">卷</option>
                        <option value="CHAPTER">章</option>
                        <option value="SCENE">节</option>
                      </select>
                              <button class="btn btn-sm btn-save" :disabled="updateLoading" @click="handleUpdate(grand.id)">保存</button>
                      <button class="btn btn-sm btn-cancel-sm" @click="cancelEdit">取消</button>
                    </div>
                  </template>

                  <template v-else>
                    <div class="node-body">
                      <span class="type-badge" :style="{ background: getTypeColor(grand.node_type) + '18', color: getTypeColor(grand.node_type) }">
                        {{ getTypeLabel(grand.node_type) }}
                      </span>
                      <span class="node-title">{{ grand.title }}</span>
                      <span v-if="(grand.node_type === 'CHAPTER' || grand.node_type === 'SCENE') && grand.word_count" class="word-badge">{{ grand.word_count }} 字</span>
                      <span v-if="grand.node_type === 'CHAPTER' || grand.node_type === 'SCENE'" class="status-dot clickable" :style="{ background: getStatusColor(grand.status) }" :title="getStatusLabel(grand.status) + ' — 点击切换'" @click.stop="cycleStatus(grand)"></span>
                            </div>
                    <div class="node-actions">
                      <router-link
                        v-if="grand.node_type === 'CHAPTER' || grand.node_type === 'SCENE'"
                        :to="`/books/${bookId}/write/${grand.id}?title=${encodeURIComponent(grand.title)}`"
                        class="btn-action btn-write"
                        title="写正文"
                      >
                        写正文
                      </router-link>
                      <button class="btn-action" title="编辑" @click="startEdit(grand)">编辑</button>
                      <button class="btn-action" :title="pinnedNodes.has(grand.id) ? '取消置顶' : '置顶'" @click="togglePin(grand)">
                        {{ pinnedNodes.has(grand.id) ? '取消' : '置顶' }}
                      </button>
                      <button class="btn-action btn-action-danger" title="删除" @click="confirmDelete(grand.id, grand.title, grand.node_type)">删除</button>
                    </div>
                  </template>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ========== Bottom add-volume ── -->
    <div v-if="tree.length > 0" class="bottom-add-bar">
      <button class="btn btn-add-volume" @click="openCreateForm(null)">+ 新建卷</button>
    </div>
    </template>

    <!-- ========== Table View ========== -->
    <template v-else-if="viewMode === 'table'">
      <div class="table-toolbar">
        <div class="table-filters">
          <select v-model="tableFilterType" class="table-filter-select">
            <option value="">全部类型</option>
            <option value="CHAPTER">章</option>
            <option value="SCENE">节</option>
          </select>
          <select v-model="tableFilterStatus" class="table-filter-select">
            <option value="">全部状态</option>
            <option value="DRAFT">草稿</option>
            <option value="WRITING">写作中</option>
            <option value="REVISION">修订中</option>
            <option value="COMPLETED">已完成</option>
          </select>
          <span class="table-count">{{ filteredFlatNodes.length }} 项</span>
        </div>
      </div>
      <div class="table-container" v-if="filteredFlatNodes.length">
        <table class="outline-table">
          <thead>
            <tr>
              <th class="col-seq" @click="toggleSort('sequence')"># <span class="sort-arrow">{{ tableSortBy === 'sequence' ? (tableSortDir === 'asc' ? '↑' : '↓') : '' }}</span></th>
              <th class="col-type">类型</th>
              <th class="col-title" @click="toggleSort('title')">标题 <span class="sort-arrow">{{ tableSortBy === 'title' ? (tableSortDir === 'asc' ? '↑' : '↓') : '' }}</span></th>
              <th class="col-parent">所属卷</th>
              <th class="col-words" @click="toggleSort('words')">字数 <span class="sort-arrow">{{ tableSortBy === 'words' ? (tableSortDir === 'asc' ? '↑' : '↓') : '' }}</span></th>
              <th class="col-status" @click="toggleSort('status')">状态 <span class="sort-arrow">{{ tableSortBy === 'status' ? (tableSortDir === 'asc' ? '↑' : '↓') : '' }}</span></th>
              <th class="col-action">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="node in filteredFlatNodes" :key="node.id" :class="{ 'row-completed': node.status === 'COMPLETED' }">
              <td class="col-seq">{{ node.sequence }}</td>
              <td class="col-type">
                <span class="type-badge" :style="{ background: getTypeColor(node.node_type) + '18', color: getTypeColor(node.node_type) }">{{ getTypeLabel(node.node_type) }}</span>
              </td>
              <td class="col-title">
                <router-link :to="`/books/${bookId}/write/${node.id}?title=${encodeURIComponent(node.title)}&node_type=${node.node_type}`" class="table-title-link">{{ node.title }}</router-link>
              </td>
              <td class="col-parent">{{ node._parentTitle || '-' }}</td>
              <td class="col-words">{{ node.word_count || 0 }} 字</td>
              <td class="col-status"><span class="status-dot" :style="{ background: getStatusColor(node.status) }" :title="getStatusLabel(node.status)"></span> {{ getStatusLabel(node.status) }}</td>
              <td class="col-action">
                <router-link :to="`/books/${bookId}/write/${node.id}?title=${encodeURIComponent(node.title)}&node_type=${node.node_type}`" class="btn-action btn-write">写正文</router-link>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="table-empty">没有匹配的章节</div>
      <div v-if="tree.length > 0" class="bottom-add-bar">
        <button class="btn btn-add-volume" @click="openCreateForm(null)">+ 新建卷</button>
      </div>
    </template>

    <!-- ========== Confirm Delete ========== -->
    <ModalConfirm
      :visible="deleteConfirm.show"
      :danger="true"
      title="确认删除"
      :message="`确定删除「${deleteConfirm.title}」吗？${deleteMsg}`"
      @confirm="handleDelete"
      @cancel="cancelDelete"
    />

    <!-- ========== Random Preview ========== -->
    <RandomPreviewModal
      :visible="preview.show"
      :type="preview.type"
      :data="preview.data"
      @apply="applyOutline"
      @draft="draftOutline"
      @close="closePreview"
    />
  </div>
</template>

<script setup>
import { ref, computed, reactive, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getOutline, createOutlineNode, updateOutlineNode, updateOutlineNodeStatus, deleteOutlineNode, getBook, updateBook } from '@/api/book'
import { randomOutline, randomOutlineExpand, getGenerationStatus } from '@/api/generation'
import { useRequest } from '@/composables/useRequest'
import { useDrafts } from '@/composables/useDrafts'
import ModalConfirm from '@/components/ModalConfirm.vue'
import RandomPreviewModal from '@/components/RandomPreviewModal.vue'
import MindMapView from '@/components/MindMapView.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import DraftsDrawer from '@/components/DraftsDrawer.vue'
import { NODE_TYPE, NODE_COLOR } from '@/utils/labels'

const route = useRoute()
const bookId = route.params.id
const { add: addDraft } = useDrafts(bookId)

const {
  loading: fetchLoading, error: fetchError, execute: fetchExecute,
} = useRequest(getOutline)
const {
  loading: createLoading, error: createError, execute: createExecute,
} = useRequest(createOutlineNode)
const {
  loading: updateLoading, error: updateError, execute: updateExecute,
} = useRequest(updateOutlineNode)
const {
  loading: deleteLoading, error: deleteError, execute: deleteExecute,
} = useRequest(deleteOutlineNode)

// ── 总纲 ───────────────────────────
const synopsis = ref('')
const oneSentence = ref('')
const synopsisSaving = ref(false)
const synopsisSaved = ref(false)
let synopsisTimer = null
const bookTitle = ref('')

async function loadSynopsis() {
  const res = await getBook(bookId)
  if (res) {
    synopsis.value = res.data?.core_idea || ''
    oneSentence.value = res.data?.one_sentence || ''
    bookTitle.value = res.data?.title || ''
  }
}

async function saveSynopsis() {
  if (synopsisSaving.value) return
  synopsisSaving.value = true
  synopsisSaved.value = false
  const res = await updateBook(bookId, { core_idea: synopsis.value })
  if (res) {
    synopsisSaved.value = true
    if (synopsisTimer) clearTimeout(synopsisTimer)
    synopsisTimer = setTimeout(() => { synopsisSaved.value = false }, 2000)
  }
  synopsisSaving.value = false
}

// ── Tree state ──────────────────────
const nodes = ref([])
const tree = ref([])
const showCreateForm = ref(false)
const createForm = ref({ title: '', node_type: 'VOLUME', sequence: 0, parent_id: null })
const createParentTitle = ref('')
const editingId = ref(null)
const editForm = ref({ title: '', node_type: 'VOLUME', sequence: 0 })
const deleteConfirm = ref({ show: false, id: null, title: '', nodeType: '' })
const successMsg = ref('')
const errorMsg = ref('')
let successTimer = null

const outlineGenerating = ref(false)

const viewMode = ref('tree') // 'tree' | 'mindmap' | 'table'
const tableSortBy = ref('sequence')
const tableSortDir = ref('asc')
const tableFilterType = ref('')
const tableFilterStatus = ref('')

// Flatten tree to array of writable nodes (CHAPTER/SCENE) with parent info
const flatNodes = computed(() => {
  const result = []
  let order = 0
  function walk(nodes, parentTitle) {
    for (const n of nodes) {
      if (n.node_type === 'CHAPTER' || n.node_type === 'SCENE') {
        result.push({ ...n, _parentTitle: parentTitle, _treeOrder: order++ })
      }
      if (n.children) walk(n.children, n.node_type === 'VOLUME' ? n.title : parentTitle)
    }
  }
  walk(tree.value, '')
  return result
})

const filteredFlatNodes = computed(() => {
  let list = [...flatNodes.value]
  if (tableFilterType.value) list = list.filter(n => n.node_type === tableFilterType.value)
  if (tableFilterStatus.value) list = list.filter(n => n.status === tableFilterStatus.value)
  const dir = tableSortDir.value === 'asc' ? 1 : -1
  list.sort((a, b) => {
    switch (tableSortBy.value) {
      case 'title': return dir * a.title.localeCompare(b.title, 'zh')
      case 'words': return dir * ((a.word_count || 0) - (b.word_count || 0))
      case 'status': return dir * a.status.localeCompare(b.status)
      case 'sequence': return dir * (a.sequence - b.sequence)
      default: return dir * (a._treeOrder - b._treeOrder)
    }
  })
  return list
})

function toggleSort(column) {
  if (tableSortBy.value === column) {
    tableSortDir.value = tableSortDir.value === 'asc' ? 'desc' : 'asc'
  } else {
    tableSortBy.value = column
    tableSortDir.value = 'asc'
  }
}

const preview = ref({ show: false, type: 'outline', data: null })
const genStatus = ref({})
const statusWarning = ref('')

async function checkStatus() {
  try {
    const s = await getGenerationStatus(bookId)
    genStatus.value = s
    if (!s.world_setting) {
      statusWarning.value = '建议先生成「世界观设定」，再编排大纲。'
    } else {
      statusWarning.value = ''
    }
  } catch { /* ignore */ }
}

async function handleRandomOutline() {
  if (!synopsis.value.trim()) {
    errorMsg.value = '请先在上方填写总纲（全书概要），再生成大纲'
    return
  }
  outlineGenerating.value = true
  errorMsg.value = ''
  try {
    const data = await randomOutline(bookId, synopsis.value.trim())
    if (!data.volumes || !data.volumes.length) {
      errorMsg.value = '生成失败，未返回有效结构'
      return
    }
    preview.value = { show: true, type: 'outline', data }
  } catch (e) {
    errorMsg.value = e.message || '生成失败'
  } finally {
    outlineGenerating.value = false
  }
}

async function handleOutlineExpand() {
  if (!oneSentence.value.trim()) {
    errorMsg.value = '请先在书籍详情页填写一句话梗概'
    return
  }
  outlineGenerating.value = true
  errorMsg.value = ''
  try {
    const data = await randomOutlineExpand(bookId, oneSentence.value.trim())
    if (!data.volumes || !data.volumes.length) {
      errorMsg.value = '生成失败，未返回有效结构'
      return
    }
    preview.value = { show: true, type: 'outline', data }
  } catch (e) {
    errorMsg.value = e.message || '生成失败'
  } finally {
    outlineGenerating.value = false
  }
}

async function applyOutline() {
  errorMsg.value = ''
  const data = preview.value.data
  if (!data?.volumes) return
  outlineGenerating.value = true
  try {
    // Create nodes sequentially: volumes → chapters → scenes
    for (let vi = 0; vi < data.volumes.length; vi++) {
      const vol = data.volumes[vi]
      const volRes = await createOutlineNode(bookId, {
        title: vol.title,
        node_type: 'VOLUME',
        sequence: vi,
        parent_id: null,
      })
      if (!volRes?.data) continue
      const volumeId = volRes.data.id

      if (vol.chapters) {
        for (let ci = 0; ci < vol.chapters.length; ci++) {
          const ch = vol.chapters[ci]
          const chRes = await createOutlineNode(bookId, {
            title: ch.title,
            node_type: 'CHAPTER',
            sequence: ci,
            parent_id: volumeId,
          })
          if (!chRes?.data) continue
          const chapterId = chRes.data.id

          if (ch.scenes) {
            for (let si = 0; si < ch.scenes.length; si++) {
              await createOutlineNode(bookId, {
                title: ch.scenes[si].title,
                node_type: 'SCENE',
                sequence: si,
                parent_id: chapterId,
              })
            }
          }
        }
      }
    }

    await fetchOutline()
    showSuccess('大纲生成完成')
    preview.value = { show: false, type: 'outline', data: null }
  } catch (e) {
    errorMsg.value = e.message || '生成失败'
  } finally {
    outlineGenerating.value = false
  }
}

function draftOutline() {
  addDraft('outline', preview.value.data, '大纲 ' + new Date().toLocaleString())
  preview.value = { show: false, type: 'outline', data: null }
  showSuccess('已存入草稿箱')
}

function handleDraftApply(data) {
  preview.value.data = data
  applyOutline()
}

function closePreview() {
  preview.value = { show: false, type: 'outline', data: null }
}

// ── Volume synopsis / Chapter notes ─
const goalOpen = ref(new Set())
const goalCache = ref({})

// ── Collapse / Expand (persisted) ─
const COL_KEY = `youmo_outline_collapsed_${bookId}`
const collapsedNodes = ref(new Set(loadCollapsed()))

function loadCollapsed() {
  try { return JSON.parse(localStorage.getItem(COL_KEY) || '[]') } catch { return [] }
}
function saveCollapsed() {
  localStorage.setItem(COL_KEY, JSON.stringify([...collapsedNodes.value]))
}

const allExpanded = computed(() => collapsedNodes.value.size === 0)

function handleMindMapNavigate(node) {
  setFocused(node)
}

function toggleCollapse(nodeId) {
  const next = new Set(collapsedNodes.value)
  if (next.has(nodeId)) { next.delete(nodeId) }
  else { next.add(nodeId) }
  collapsedNodes.value = next
  saveCollapsed()
}

function expandAll() {
  if (collapsedNodes.value.size) {
    collapsedNodes.value = new Set()
  } else {
    const parentIds = new Set(nodes.value.map(n => n.parent_id).filter(Boolean))
    const toAdd = nodes.value.filter(n => canHaveChildren(n.node_type) && parentIds.has(n.id)).map(n => n.id)
    collapsedNodes.value = new Set(toAdd)
  }
  saveCollapsed()
}

// ── Last focused node (auto-scroll / highlight) ─
const FOCUS_KEY = `youmo_outline_focus_${bookId}`
const lastFocusedId = ref(loadFocus())

function loadFocus() {
  try { return JSON.parse(localStorage.getItem(FOCUS_KEY)) } catch { return null }
}

function setFocused(node) {
  lastFocusedId.value = node.id
  localStorage.setItem(FOCUS_KEY, JSON.stringify(node.id))
  // auto-expand parent chain
  const next = new Set(collapsedNodes.value)
  let p = nodes.value.find(n => n.id === node.parent_id)
  while (p) {
    next.delete(p.id)
    p = nodes.value.find(n => n.id === p.parent_id)
  }
  collapsedNodes.value = next
  saveCollapsed()
}

// ── Drag & Drop ─────────────────────
const dragNode = ref(null)
const dragOverId = ref(null)   // node we're hovering over
const dropPosition = ref(null) // 'before' | 'after'

function handleDragStart(node) {
  dragNode.value = node
}

function handleDragOver(e, node) {
  e.preventDefault()
  if (!dragNode.value || dragNode.value.id === node.id) return
  // only reorder within same parent
  if (dragNode.value.parent_id !== node.parent_id) return
  dragOverId.value = node.id
  // drop above or below based on mouse Y within the row
  const rect = e.currentTarget.getBoundingClientRect()
  dropPosition.value = e.clientY < rect.top + rect.height / 2 ? 'before' : 'after'
}

function handleDragLeave() {
  dragOverId.value = null
  dropPosition.value = null
}

async function handleDrop(e, targetNode) {
  e.preventDefault()
  if (!dragNode.value || dragNode.value.id === targetNode.id) return
  if (dragNode.value.parent_id !== targetNode.parent_id) return

  const parentId = targetNode.parent_id
  const siblings = nodes.value.filter(n => n.parent_id === parentId).sort((a, b) => a.sequence - b.sequence)
  const dragged = siblings.find(n => n.id === dragNode.value.id)
  const target = siblings.find(n => n.id === targetNode.id)
  if (!dragged || !target) return

  // remove dragged, insert at new position
  const rest = siblings.filter(n => n.id !== dragged.id)
  const targetIdx = rest.findIndex(n => n.id === target.id)
  const insertIdx = dropPosition.value === 'before' ? targetIdx : targetIdx + 1
  rest.splice(insertIdx, 0, dragged)

  // reassign sequences
  const updates = []
  rest.forEach((n, i) => {
    if (n.sequence !== i) {
      updates.push(updateExecute(bookId, n.id, { sequence: i }))
    }
  })

  await Promise.all(updates)
  await fetchOutline()

  dragNode.value = null
  dragOverId.value = null
  dropPosition.value = null
}

function handleDragEnd() {
  dragNode.value = null
  dragOverId.value = null
  dropPosition.value = null
}

function dragOverClass(node) {
  if (dragOverId.value !== node.id) return ''
  return dropPosition.value === 'before' ? 'drop-before' : 'drop-after'
}

// ── Pin to top (UI-level, toggle, localStorage persisted) ─
const PIN_KEY = `youmo_outline_pins_${bookId}`
const pinnedNodes = reactive(new Set(loadPinned()))

function loadPinned() {
  try { return JSON.parse(localStorage.getItem(PIN_KEY) || '[]') } catch { return [] }
}

function savePinned() {
  localStorage.setItem(PIN_KEY, JSON.stringify([...pinnedNodes]))
}

function togglePin(node) {
  if (pinnedNodes.has(node.id)) {
    pinnedNodes.delete(node.id)
  } else {
    pinnedNodes.add(node.id)
  }
  savePinned()
  // re-sort in place without API call
  tree.value = buildTree([...nodes.value].sort((a, b) => a.sequence - b.sequence))
  sortTree(tree.value)
}

function toggleGoal(node) {
  const s = new Set(goalOpen.value)
  if (s.has(node.id)) {
    s.delete(node.id)
  } else {
    s.add(node.id)
    if (!(node.id in goalCache.value)) {
      goalCache.value[node.id] = node.writing_goal || ''
    }
  }
  goalOpen.value = s
}

async function saveGoal(id) {
  const text = (goalCache.value[id] || '').trim()
  const node = nodes.value.find(n => n.id === id)
  if (!node) return
  if (text === (node.writing_goal || '')) return
  const res = await updateExecute(bookId, id, { writing_goal: text })
  if (res) {
    node.writing_goal = text
    showSuccess('已保存')
  }
}

const deleteMsg = computed(() => {
  const t = deleteConfirm.value.nodeType
  if (t === 'VOLUME') return '其下的所有章和节将被一并删除。'
  if (t === 'CHAPTER') return '其下的所有节将被一并删除。'
  return '此操作不可撤销。'
})

// ── Tree builder ────────────────────
function buildTree(flatNodes) {
  const map = {}
  const roots = []
  flatNodes.forEach(n => { map[n.id] = { ...n, children: [] } })
  flatNodes.forEach(n => {
    if (n.parent_id && map[n.parent_id]) {
      map[n.parent_id].children.push(map[n.id])
    } else {
      roots.push(map[n.id])
    }
  })
  return roots
}

function sortTree(nodes) {
  nodes.sort((a, b) => {
    const aPin = pinnedNodes.has(a.id) ? -1 : 0
    const bPin = pinnedNodes.has(b.id) ? -1 : 0
    if (aPin !== bPin) return aPin - bPin
    return a.sequence - b.sequence
  })
  nodes.forEach(n => { if (n.children && n.children.length) sortTree(n.children) })
}

async function fetchOutline() {
  errorMsg.value = ''
  const res = await fetchExecute(bookId)
  if (res) {
    nodes.value = res.data || []
    const sorted = [...nodes.value].sort((a, b) => a.sequence - b.sequence)
    tree.value = buildTree(sorted)
    sortTree(tree.value)
  } else {
    errorMsg.value = fetchError.value || '加载大纲失败'
  }
}

// ── Create ──────────────────────────
function openCreateForm(parentId = null) {
  errorMsg.value = ''
  successMsg.value = ''
  const parent = parentId ? nodes.value.find(n => n.id === parentId) : null
  let defaultType = 'VOLUME'
  if (parent && parent.node_type === 'VOLUME') defaultType = 'CHAPTER'
  else if (parent && parent.node_type === 'CHAPTER') defaultType = 'SCENE'
  // auto-sequence: next number after existing siblings
  const siblings = nodes.value.filter(n => n.parent_id === parentId)
  const maxSeq = siblings.length ? Math.max(...siblings.map(n => n.sequence || 0)) : -1
  createForm.value = { title: '', node_type: defaultType, sequence: maxSeq + 1, parent_id: parentId }
  createParentTitle.value = (parent && parent.title) || ''
  showCreateForm.value = true
}

function cancelCreateForm() {
  showCreateForm.value = false
  createForm.value = { title: '', node_type: 'VOLUME', sequence: 0, parent_id: null }
  createParentTitle.value = ''
}

async function handleCreate() {
  if (!createForm.value.title.trim()) { errorMsg.value = '请输入标题'; return }
  errorMsg.value = ''
  const res = await createExecute(bookId, {
    title: createForm.value.title.trim(),
    node_type: createForm.value.node_type,
    sequence: createForm.value.sequence || 0,
    parent_id: createForm.value.parent_id,
  })
  if (res) { cancelCreateForm(); showSuccess('创建成功'); await fetchOutline() }
  else { errorMsg.value = createError.value || '创建失败' }
}

// ── Update ──────────────────────────
function startEdit(node) {
  errorMsg.value = ''
  successMsg.value = ''
  editingId.value = node.id
  editForm.value = { title: node.title, node_type: node.node_type, sequence: node.sequence }
}

function cancelEdit() {
  editingId.value = null
  editForm.value = { title: '', node_type: 'VOLUME', sequence: 0 }
}

async function handleUpdate(id) {
  if (!editForm.value.title.trim()) { errorMsg.value = '请输入标题'; return }
  errorMsg.value = ''
  const res = await updateExecute(bookId, id, {
    title: editForm.value.title.trim(),
    node_type: editForm.value.node_type,
    sequence: editForm.value.sequence || 0,
  })
  if (res) { editingId.value = null; showSuccess('保存成功'); await fetchOutline() }
  else { errorMsg.value = updateError.value || '保存失败' }
}

// ── Delete ──────────────────────────
function confirmDelete(id, title, nodeType) {
  deleteConfirm.value = { show: true, id, title, nodeType }
}

function cancelDelete() {
  deleteConfirm.value = { show: false, id: null, title: '', nodeType: '' }
}

async function handleDelete() {
  const { id } = deleteConfirm.value
  if (!id) return
  errorMsg.value = ''
  const res = await deleteExecute(bookId, id)
  if (res) { cancelDelete(); showSuccess('删除成功'); await fetchOutline() }
  else { errorMsg.value = deleteError.value || '删除失败' }
}

// ── Helpers ─────────────────────────
function showSuccess(msg) {
  if (successTimer) clearTimeout(successTimer)
  successMsg.value = msg
  successTimer = setTimeout(() => { successMsg.value = '' }, 2000)
}

function getTypeLabel(type) {
  return NODE_TYPE[type] || type
}

function getTypeColor(type) {
  return NODE_COLOR[type] || '#9ca3af'
}

const NODE_STATUS_COLOR = { DRAFT: '#9ca3af', WRITING: '#3b82f6', REVISION: '#f59e0b', COMPLETED: '#10b981' }
const NODE_STATUS_LABEL = { DRAFT: '草稿', WRITING: '写作中', REVISION: '修订中', COMPLETED: '已完成' }

function getStatusColor(status) { return NODE_STATUS_COLOR[status] || '#9ca3af' }
function getStatusLabel(status) { return NODE_STATUS_LABEL[status] || status }

function volumeChapterProgress(volumeNode) {
  if (!volumeNode.children) return null
  const chapters = volumeNode.children.filter(c => c.node_type === 'CHAPTER')
  if (!chapters.length) return null
  const done = chapters.filter(c => c.status === 'COMPLETED').length
  return { done, total: chapters.length, pct: Math.round((done / chapters.length) * 100) }
}
function volumeTotalWords(volumeNode) {
  if (!volumeNode.children) return 0
  let total = 0
  function sum(nodes) {
    for (const n of nodes) {
      total += n.word_count || 0
      if (n.children) sum(n.children)
    }
  }
  sum(volumeNode.children)
  return total
}
const STATUS_CYCLE = ['DRAFT', 'WRITING', 'REVISION', 'COMPLETED']
async function cycleStatus(node) {
  const idx = STATUS_CYCLE.indexOf(node.status)
  const next = STATUS_CYCLE[(idx + 1) % STATUS_CYCLE.length]
  try {
    await updateOutlineNodeStatus(bookId, node.id, next)
    node.status = next
  } catch { errorMsg.value = '状态更新失败' }
}

function canHaveChildren(type) {
  return type === 'VOLUME' || type === 'CHAPTER'
}

function childLabel(type) {
  return type === 'VOLUME' ? '添加章' : '添加节'
}

onMounted(() => { loadSynopsis(); fetchOutline(); checkStatus() })

watch(() => route.params.id, (newId) => {
  if (newId) { loadSynopsis(); fetchOutline(); checkStatus() }
})
</script>

<style scoped>
.outline-editor {
  padding: 28px 32px;
  max-width: 960px;
  margin: 0 auto;
}

/* ── Back link ── */
.back-link {
  display: inline-block;
  color: #5b3cc4;
  text-decoration: none;
  font-size: 14px;
  margin-bottom: 12px;
  transition: color 0.2s;
}
.back-link:hover { color: #4a2fa8; }

/* ── Header ── */
.editor-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}
.editor-header h1 {
  font-size: 24px;
  font-weight: 700;
  margin: 0;
  color: #111827;
}
.header-actions { display: flex; align-items: center; gap: 8px; }
.btn-outline {
  padding: 7px 14px; font-size: 12px; font-weight: 500;
  background: var(--bg-surface); color: var(--text-secondary); border: 1px solid var(--border-input); border-radius: 6px;
  cursor: pointer; transition: all 0.15s; font-family: inherit;
}
.btn-outline:hover { border-color: #6d28d9; color: #6d28d9; }

/* ── 总纲 ── */
.synopsis-section {
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  padding: 20px 24px;
  margin-bottom: 24px;
}
.synopsis-header {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 10px;
}
.synopsis-label { font-size: 15px; font-weight: 700; color: #111827; }
.synopsis-hint { font-size: 12px; color: var(--text-muted); }
.synopsis-textarea {
  width: 100%; padding: 12px 14px;
  border: 1px solid var(--border-color); border-radius: 8px;
  font-size: 14px; line-height: 1.7; font-family: inherit;
  resize: vertical; box-sizing: border-box; color: var(--text-primary);
  transition: border-color 0.15s, box-shadow 0.15s;
}
.synopsis-textarea:focus { outline: none; border-color: #6d28d9; box-shadow: 0 0 0 3px rgba(109,40,217,0.1); }
.synopsis-textarea::placeholder { color: #c5c5c5; }
.synopsis-actions {
  display: flex; align-items: center; justify-content: flex-end; gap: 10px; margin-top: 10px;
}
.synopsis-saved { font-size: 12px; color: #059669; font-weight: 500; }
.btn-save-synopsis {
  padding: 6px 16px; font-size: 13px; background: #6d28d9; color: #fff;
  border: none; border-radius: 6px; font-weight: 500; cursor: pointer; transition: background 0.15s; font-family: inherit;
}
.btn-save-synopsis:hover:not(:disabled) { background: #5b21b6; }
.btn-save-synopsis:disabled { opacity: 0.5; cursor: not-allowed; }

/* ── Messages ── */
.msg-bar {
  padding: 10px 16px; border-radius: 8px; font-size: 14px; margin-bottom: 16px; line-height: 1.5;
}
.msg-success { background: var(--bg-success-soft); color: var(--color-success); border: 1px solid var(--border-success-soft); }
.msg-error { background: var(--bg-error-soft); color: var(--color-danger); border: 1px solid var(--border-error-soft); }

.status-warning {
  padding: 9px 16px; margin-bottom: 16px; border-radius: 6px;
  background: #fefce8; color: #854d0e; border: 1px solid #fde68a;
  font-size: 13px; display: flex; align-items: center; gap: 10px;
}
.status-link { font-weight: 500; white-space: nowrap; }
.msg-enter-active, .msg-leave-active { transition: opacity 0.25s ease, transform 0.25s ease; }
.msg-enter-from, .msg-leave-to { opacity: 0; transform: translateY(-8px); }

/* ── Buttons ── */
.btn {
  display: inline-flex; align-items: center; justify-content: center; gap: 4px;
  border: none; border-radius: 6px; font-size: 14px; font-weight: 500; cursor: pointer;
  transition: background 0.15s, color 0.15s; font-family: inherit;
}
.btn:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-primary { padding: 9px 20px; background: #6d28d9; color: #fff; }
.btn-primary:hover:not(:disabled) { background: #5b21b6; }
.btn-cancel { padding: 9px 20px; background: var(--bg-surface); color: var(--text-primary); border: 1px solid var(--border-input); }
.btn-cancel:hover:not(:disabled) { background: var(--bg-surface-hover); }
.btn-random { padding: 9px 20px; background: var(--bg-surface); color: #5b3cc4; border: 1px solid #5b3cc4; }
.btn-random:hover:not(:disabled) { background: #f5f3ff; }
.btn-lg { padding: 12px 28px; font-size: 16px; }
.btn-sm { padding: 5px 12px; font-size: 12px; border-radius: 5px; }
.btn-save { background: #059669; color: #fff; border: none; }
.btn-save:hover:not(:disabled) { background: #047857; }
.btn-cancel-sm { background: var(--bg-surface); color: var(--text-secondary); border: 1px solid var(--border-input); }
.btn-cancel-sm:hover:not(:disabled) { background: var(--bg-surface-hover); }

/* ── Create form ── */
.create-form {
  background: #fafafa; border: 1px solid var(--border-color); border-radius: 10px; padding: 20px 24px; margin-bottom: 20px;
}
.create-form-header { margin-bottom: 16px; }
.create-form-header h3 { font-size: 16px; font-weight: 600; margin: 0; color: var(--text-primary); }
.create-form-body { display: flex; flex-direction: column; gap: 12px; }
.form-field { display: flex; flex-direction: column; gap: 4px; }
.form-field label { font-size: 13px; font-weight: 500; color: var(--text-secondary); }
.form-field input, .form-field select {
  padding: 8px 12px; border: 1px solid var(--border-input); border-radius: 6px;
  font-size: 14px; font-family: inherit; background: var(--bg-surface);
  transition: border-color 0.15s, box-shadow 0.15s;
}
.form-field input:focus, .form-field select:focus { outline: none; border-color: #6d28d9; box-shadow: 0 0 0 3px rgba(109,40,217,0.1); }
.form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.form-actions { display: flex; gap: 8px; margin-top: 4px; }
.required { color: var(--color-danger); }

/* ── Empty state ── */
.empty-state { padding: 24px 0; }
.empty-card {
  background: var(--bg-surface); border: 1px solid var(--border-color); border-radius: 12px;
  padding: 44px 40px 40px; text-align: center; box-shadow: 0 1px 3px rgba(0,0,0,0.04);
}
.empty-card h2 { font-size: 22px; font-weight: 700; color: #1f2937; margin: 0 0 28px; }
.guide-steps { text-align: left; max-width: 480px; margin: 0 auto 32px; display: flex; flex-direction: column; gap: 18px; }
.guide-step { display: flex; align-items: flex-start; gap: 14px; }
.guide-step-optional { opacity: 0.65; }
.guide-icon { font-size: 24px; line-height: 1.4; flex-shrink: 0; }
.guide-step strong { font-size: 15px; color: #1f2937; display: block; margin-bottom: 2px; }
.guide-step p { font-size: 13px; color: var(--text-secondary); margin: 0; line-height: 1.5; }

/* ── Loading ── */
.state-msg { display: flex; align-items: center; justify-content: center; gap: 10px; padding: 64px 0; color: var(--text-muted); font-size: 15px; }
.loading-spinner { width: 18px; height: 18px; border: 2px solid var(--border-color); border-top-color: #6d28d9; border-radius: 50%; animation: spin 0.6s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

/* ── Tree toolbar ── */
.tree-toolbar {
  display: flex; justify-content: flex-end; margin-bottom: 8px;
}

/* ── Tree ── */
.tree-container { margin-top: 4px; }
.children { margin-left: 24px; padding-left: 12px; border-left: 2px solid var(--border-color); }

/* ── Node row ── */
.node-row {
  display: flex; align-items: center; gap: 12px;
  padding: 10px 12px; border-radius: 8px; margin-bottom: 2px; transition: background 0.15s;
}
.node-row:hover { background: var(--bg-surface-hover); }
.node-row.is-editing { background: #f5f3ff; }
.color-bar { width: 4px; min-width: 4px; height: 30px; border-radius: 2px; flex-shrink: 0; }

/* ── Fold indicator ── */
.fold-indicator {
  width: 16px; font-size: 10px; color: var(--text-muted);
  flex-shrink: 0; text-align: center; user-select: none;
}

/* ── Node volume (framed) ── */
.node-volume {
  background: #fafbfd;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  margin-bottom: 12px;
  padding: 12px 16px;
}
.node-volume:hover { border-color: #c7c9ed; background: #f8f7fc; }

/* ── Node chapter (framed) ── */
.node-chapter {
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  margin-bottom: 4px;
  padding: 9px 14px;
}
.node-chapter:hover { border-color: #d9d6e8; background: #faf9fd; }

/* ── Node body ── */
.node-body { flex: 1; display: flex; align-items: center; gap: 10px; min-width: 0; }
.node-volume, .node-chapter { cursor: pointer; }
.type-badge { font-size: 11px; font-weight: 600; padding: 2px 10px; border-radius: 10px; white-space: nowrap; line-height: 1.6; }
.node-title { font-size: 15px; font-weight: 500; color: #1f2937; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.word-badge {
  font-size: 11px; font-weight: 500; color: #6b7280;
  background: #f3f4f6; padding: 1px 7px; border-radius: 10px;
  white-space: nowrap; flex-shrink: 0;
}
.status-dot {
  width: 8px; height: 8px; border-radius: 50%;
  flex-shrink: 0; cursor: default;
}
.status-dot.clickable { cursor: pointer; }
.status-dot.clickable:hover { transform: scale(1.4); transition: transform 0.15s; }

.volume-progress-bar {
  width: 48px; height: 4px; background: #e5e7eb;
  border-radius: 2px; flex-shrink: 0; overflow: hidden;
}
.volume-progress-fill {
  height: 100%; background: #10b981; border-radius: 2px;
  transition: width 0.3s;
}
.volume-progress-text {
  font-size: 11px; color: #059669; font-weight: 500;
  white-space: nowrap; flex-shrink: 0;
}
.volume-words {
  font-size: 11px; color: #6b7280; font-weight: 400;
  white-space: nowrap; flex-shrink: 0;
}
.node-seq { font-size: 12px; color: var(--text-muted); white-space: nowrap; }

/* ── Note toggle (概要/备注 button) ── */
.note-toggle {
  font-size: 11px; padding: 2px 8px;
  border: 1px solid var(--border-color); border-radius: 4px;
  background: var(--bg-surface); color: var(--text-muted);
  cursor: pointer; white-space: nowrap;
  transition: all 0.15s; font-family: inherit;
}
/* 概要 — 紫色系 */
.note-toggle.volume:hover { border-color: #7c3aed; color: #7c3aed; }
.note-toggle.volume.has-content { color: #7c3aed; border-color: #c4b5fd; background: #f5f3ff; }
.note-toggle.volume.has-content:hover { border-color: #7c3aed; color: #5b21b6; }
/* 备注 — 琥珀色系 */
.note-toggle.chapter:hover { border-color: #d97706; color: #d97706; }
.note-toggle.chapter.has-content { color: #d97706; border-color: #fde68a; background: #fffbeb; }
.note-toggle.chapter.has-content:hover { border-color: #d97706; color: #b45309; }

/* ── Goal panel (expandable textarea) ── */
.goal-panel {
  margin: 0 0 4px 16px; padding: 8px 12px 10px;
  background: #fafafa; border: 1px solid var(--border-color); border-radius: 6px;
}
/* 概要面板 — 紫色边框 */
.goal-panel.volume { border-color: #c4b5fd; background: #faf9ff; }
/* 备注面板 — 琥珀边框 */
.goal-panel.chapter { border-color: #fde68a; background: #fffdf7; }
.goal-textarea {
  width: 100%; padding: 8px 10px;
  border: 1px solid var(--border-color); border-radius: 6px;
  font-size: 13px; line-height: 1.6; font-family: inherit;
  resize: vertical; box-sizing: border-box; color: var(--text-primary);
  transition: border-color 0.15s;
}
.goal-textarea.volume:focus { outline: none; border-color: #7c3aed; }
.goal-textarea.chapter:focus { outline: none; border-color: #d97706; }
.goal-textarea::placeholder { color: #c5c5c5; }

/* ── Drag & Drop ── */
.is-dragging { opacity: 0.4; }
.drop-before { border-top: 2px solid #6d28d9 !important; }
.drop-after { border-bottom: 2px solid #6d28d9 !important; }
.node-row { transition: opacity 0.15s, border-color 0.15s; }
.is-focused { box-shadow: inset 3px 0 0 #6d28d9; }

/* ── Node actions ── */
.node-actions { display: flex; align-items: center; gap: 4px; opacity: 0; transition: opacity 0.12s; flex-shrink: 0; }
.node-row:hover .node-actions { opacity: 1; }
.btn-action {
  padding: 3px 10px; font-size: 12px; border: 1px solid var(--border-color); border-radius: 5px;
  background: var(--bg-surface); color: var(--text-secondary); cursor: pointer; white-space: nowrap;
  transition: background 0.12s, color 0.12s, border-color 0.12s; font-family: inherit;
}
.btn-action:hover { background: var(--bg-surface-hover); color: var(--text-primary); border-color: #d1d5db; }
.btn-action-danger { color: #dc2626; border-color: #fecaca; }
.btn-action-danger:hover { background: #fef2f2; color: #b91c1c; border-color: #fca5a5; }
.btn-write {
  color: #5b3cc4 !important; border-color: #d4c5f0 !important; text-decoration: none !important;
}
.btn-write:hover { background: #f5f3ff !important; color: #4a2fa8 !important; border-color: #b8a0e0 !important; }

/* ── Inline edit ── */
.edit-inline { flex: 1; display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.edit-inline input, .edit-inline select {
  padding: 5px 10px; border: 1px solid var(--border-input); border-radius: 5px;
  font-size: 13px; font-family: inherit; background: var(--bg-surface);
  transition: border-color 0.15s, box-shadow 0.15s;
}
.edit-inline input:focus, .edit-inline select:focus { outline: none; border-color: #6d28d9; box-shadow: 0 0 0 3px rgba(109,40,217,0.1); }
.edit-inline .seq-input { width: 70px; }

/* ── Bottom add bar ── */
.bottom-add-bar {
  margin-top: 20px; padding: 16px 0;
  border-top: 1px dashed var(--border-color);
  text-align: center;
}
.btn-add-volume {
  padding: 10px 32px; font-size: 14px; font-weight: 500;
  background: var(--bg-surface); color: #6d28d9; border: 2px dashed #c4b5fd; border-radius: 10px;
  cursor: pointer; transition: all 0.15s; font-family: inherit;
}
.btn-add-volume:hover { background: #f5f3ff; border-color: #6d28d9; }

/* ── View Mode Group ── */
.view-mode-group {
  display: flex; gap: 2px;
  border: 1px solid var(--border-color); border-radius: 6px;
  overflow: hidden;
}
.view-mode-group .btn {
  border: none; border-radius: 0; padding: 6px 12px;
  font-size: 12px; font-weight: 500;
}
.view-mode-group .btn:not(:last-child) { border-right: 1px solid var(--border-color); }

/* ── Table View ── */
.table-toolbar {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 12px;
}
.table-filters { display: flex; align-items: center; gap: 8px; }
.table-filter-select {
  padding: 6px 10px; font-size: 12px; font-family: inherit;
  border: 1px solid var(--border-input); border-radius: 6px;
  background: var(--bg-surface); color: var(--text-primary);
}
.table-count { font-size: 12px; color: var(--text-muted); }
.table-container {
  background: var(--bg-surface); border: 1px solid var(--border-color);
  border-radius: 8px; overflow: hidden;
}
.outline-table {
  width: 100%; border-collapse: collapse; font-size: 13px;
}
.outline-table thead { background: var(--bg-surface-hover); }
.outline-table th {
  padding: 10px 14px; text-align: left; font-weight: 600;
  color: var(--text-secondary); font-size: 12px;
  white-space: nowrap; cursor: default; user-select: none;
}
.outline-table th .sort-arrow { font-size: 10px; color: var(--color-brand); }
.outline-table td {
  padding: 10px 14px; border-top: 1px solid var(--border-color);
  color: var(--text-primary); vertical-align: middle;
}
.outline-table tbody tr:hover { background: var(--bg-surface-hover); }
.outline-table tbody tr.row-completed { opacity: 0.6; }
.outline-table tbody tr.row-completed:hover { opacity: 1; }
.col-seq { width: 40px; color: var(--text-muted); font-size: 12px; }
.col-type { width: 50px; }
.col-parent { width: 120px; color: var(--text-muted); font-size: 12px; }
.col-words { width: 80px; font-variant-numeric: tabular-nums; }
.col-status { width: 90px; }
.col-action { width: 70px; text-align: right; }
.table-title-link {
  color: var(--color-brand); text-decoration: none; font-weight: 500;
}
.table-title-link:hover { text-decoration: underline; }
.table-empty {
  text-align: center; padding: 48px 0; color: var(--text-muted); font-size: 14px;
}
.outline-table .type-badge {
  font-size: 10px; padding: 1px 6px; border-radius: 4px; font-weight: 500;
}

/* ── Responsive ── */
@media (max-width: 768px) {
  .outline-editor { padding: 20px 16px; }
  .empty-card { padding: 32px 20px 28px; }
  .synopsis-section { padding: 16px 18px; }
}
@media (max-width: 640px) {
  .editor-header { flex-direction: column; align-items: stretch; gap: 12px; }
  .editor-header h1 { font-size: 20px; }
  .form-row { grid-template-columns: 1fr; }
  .node-actions { opacity: 1; }
  .edit-inline { flex-direction: column; align-items: stretch; }
  .edit-inline .seq-input { width: 100%; }
  .empty-card { padding: 24px 16px 20px; }
  .guide-steps { gap: 14px; }
  .synopsis-header { flex-direction: column; gap: 4px; }
}
</style>
