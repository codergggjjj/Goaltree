<script setup lang="ts">
import { nextTick, reactive, ref, watch } from 'vue'
import { ExternalLink, Link2, Pencil, Plus, Trash2 } from '@lucide/vue'
import { getApiProblem, goalApi } from './goalApi'
import type { GoalLink } from './types'

const props = defineProps<{ goalId: string }>()
const links = ref<GoalLink[]>([])
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const fieldError = ref('')
const formOpen = ref(false)
const editingId = ref('')
const form = reactive({ name: '', url: '' })
const nameInput = ref<HTMLInputElement | null>(null)
let requestSerial = 0

async function loadLinks(id = props.goalId) {
  const serial = ++requestSerial
  loading.value = true
  error.value = ''
  links.value = []
  formOpen.value = false
  try {
    const loaded = await goalApi.listLinks(id)
    if (serial === requestSerial) links.value = loaded
  } catch (caught) {
    if (serial === requestSerial) error.value = getApiProblem(caught).message
  } finally {
    if (serial === requestSerial) loading.value = false
  }
}
watch(() => props.goalId, (id) => { void loadLinks(id) }, { immediate: true })

function startAdd() {
  editingId.value = ''
  form.name = ''
  form.url = ''
  fieldError.value = ''
  formOpen.value = true
  void nextTick(() => nameInput.value?.focus())
}

function startEdit(link: GoalLink) {
  editingId.value = link.id
  form.name = link.name
  form.url = link.url
  fieldError.value = ''
  formOpen.value = true
  void nextTick(() => nameInput.value?.focus())
}

function validUrl(value: string): string | null {
  try {
    const url = new URL(value.trim())
    if (!['http:', 'https:'].includes(url.protocol) || !url.hostname || url.href.length > 2048) return null
    return url.href
  } catch { return null }
}

async function saveLink() {
  if (saving.value) return
  if (!form.name.trim()) { fieldError.value = '请输入链接名称'; return }
  const url = validUrl(form.url)
  if (!url) { fieldError.value = '请输入有效的 http 或 https 链接'; return }
  fieldError.value = ''
  error.value = ''
  saving.value = true
  try {
    const input = { name: form.name.trim(), url }
    const result = editingId.value
      ? await goalApi.updateLink(props.goalId, editingId.value, input)
      : await goalApi.createLink(props.goalId, input)
    links.value = editingId.value
      ? links.value.map((link) => link.id === result.id ? result : link)
      : [...links.value, result]
    formOpen.value = false
  } catch (caught) {
    error.value = getApiProblem(caught).message
  } finally {
    saving.value = false
  }
}

async function removeLink(link: GoalLink) {
  if (saving.value) return
  error.value = ''
  saving.value = true
  try {
    await goalApi.deleteLink(props.goalId, link.id)
    links.value = links.value.filter((item) => item.id !== link.id)
    if (editingId.value === link.id) formOpen.value = false
  } catch (caught) {
    error.value = getApiProblem(caught).message
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="goal-links">
    <div class="goal-links-heading">
      <h3><Link2 :size="19" aria-hidden="true" />关联链接</h3>
      <button v-if="links.length && !formOpen" type="button" class="secondary-button" @click="startAdd"><Plus :size="16" aria-hidden="true" />添加链接</button>
    </div>
    <p v-if="loading" role="status" class="goal-links-message">正在加载链接…</p>
    <p v-else-if="error && !links.length && !formOpen" role="alert" class="goal-links-message">{{ error }} <button type="button" @click="loadLinks()">重试</button></p>
    <template v-else>
      <div v-if="links.length" class="goal-links-list">
        <div v-for="link in links" :key="link.id" class="goal-link-row">
          <a :href="link.url" target="_blank" rel="noopener noreferrer" :aria-label="link.name"><ExternalLink :size="17" aria-hidden="true" /><span><strong>{{ link.name }}</strong><small>{{ link.url }}</small></span></a>
          <button type="button" :disabled="saving" :aria-label="`编辑${link.name}`" title="编辑链接" @click="startEdit(link)"><Pencil :size="16" aria-hidden="true" /></button>
          <button type="button" :disabled="saving" :aria-label="`删除${link.name}`" title="删除链接" @click="removeLink(link)"><Trash2 :size="16" aria-hidden="true" /></button>
        </div>
      </div>
      <div v-else-if="!formOpen" class="goal-links-empty"><p>还没有关联链接</p><button type="button" class="secondary-button" @click="startAdd"><Plus :size="16" aria-hidden="true" />添加链接</button></div>
      <form v-if="formOpen" class="goal-link-form" @submit.prevent="saveLink">
        <label>链接名称<input ref="nameInput" v-model="form.name" maxlength="120" placeholder="例如：学习资料" /></label>
        <label>链接 URL<input v-model="form.url" type="url" maxlength="2048" placeholder="https://example.com" /></label>
        <p v-if="fieldError" class="field-error" role="alert">{{ fieldError }}</p>
        <p v-if="error" class="field-error" role="alert">{{ error }}</p>
        <div class="goal-link-form-actions"><button type="button" class="secondary-button" :disabled="saving" @click="formOpen = false">取消</button><button type="submit" class="primary-button" :disabled="saving">{{ saving ? '正在保存…' : '保存链接' }}</button></div>
      </form>
    </template>
  </div>
</template>
