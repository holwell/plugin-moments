<script lang="ts" setup>
import { IconArrowDown, IconClose, VDropdown, VDropdownItem } from "@halo-dev/components";
import { computed } from "vue";

const props = withDefaults(
  defineProps<{
    items: {
      label: string;
      value?: string | boolean | number;
    }[];
    label: string;
    modelValue?: string | boolean | number;
    /**
     * 是否允许清空当前选择。排序等「必须始终有值」的场景需要关掉，
     * 否则再次点击已选项会把值置空。
     */
    clearable?: boolean;
  }>(),
  {
    modelValue: undefined,
    clearable: true,
  }
);

const emit = defineEmits<{
  (event: "update:modelValue", modelValue: string | boolean | number | undefined): void;
}>();

const selectedItem = computed(() => {
  return props.items.find((item) => item.value === props.modelValue);
});

function handleSelect(item: { label: string; value?: string | boolean | number }) {
  if (props.clearable && item.value === props.modelValue) {
    emit("update:modelValue", undefined);
    return;
  }
  emit("update:modelValue", item.value);
}

const handleClear = (event: Event) => {
  emit("update:modelValue", undefined);
  event.stopPropagation();
};
</script>

<template>
  <VDropdown>
    <div
      class=":uno: group flex cursor-pointer select-none items-center border rounded-lg px-3 text-sm text-gray-700 leading-9 hover:text-black"
      :class="{
        ':uno: font-semibold text-gray-700': modelValue !== undefined,
      }"
    >
      <span v-if="!selectedItem" class=":uno: mr-0.5">
        {{ label }}
      </span>
      <span v-else class=":uno: mr-0.5"> {{ label }}：{{ selectedItem.label }} </span>
      <span class=":uno: text-base">
        <IconArrowDown
          :class="{ ':uno: group-hover:hidden': clearable && modelValue !== undefined }"
        />
        <IconClose
          v-if="clearable && modelValue !== undefined"
          class=":uno: hidden group-hover:block"
          @click="handleClear"
        />
      </span>
    </div>
    <template #popper>
      <VDropdownItem
        v-for="(item, index) in items"
        :key="index"
        :selected="item.value === modelValue"
        @click="handleSelect(item)"
      >
        {{ item.label }}
      </VDropdownItem>
    </template>
  </VDropdown>
</template>
