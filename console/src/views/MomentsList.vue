<script lang="ts" setup>
import { momentsConsoleApiClient } from "@/api";
import DateRangeFilter from "@/components/DateRangeFilter.vue";
import FilterDropdown from "@/components/FilterDropdown.vue";
import MomentEdit from "@/components/MomentEdit.vue";
import MomentItem from "@/components/MomentItem.vue";
import TagFilterDropdown from "@/components/TagFilterDropdown.vue";
import { usePluginShikiScriptLoader } from "@/plugin-supports/shiki/use-plugin-shiki-script-loader";
import { axiosInstance } from "@halo-dev/api-client";
import {
  IconExternalLinkLine,
  VButton,
  VCard,
  VLoading,
  VPageHeader,
  VPagination,
} from "@halo-dev/components";
import { utils } from "@halo-dev/ui-shared";
import { useQuery } from "@tanstack/vue-query";
import { useRouteQuery } from "@vueuse/router";
import { computed, provide, ref, watch } from "vue";
import { useRouter } from "vue-router";
import MingcuteMomentsLine from "~icons/mingcute/moment-line";

interface ViewScopeVo {
  enabled: boolean;
  mode: string;
  count?: number;
  since?: string;
  rangeValue?: number;
  rangeUnit?: string;
  startTime?: string;
  restricted: boolean;
  text: string;
}

const tag = useRouteQuery<string>("tag");
const selectedApprovedStatus = useRouteQuery<string | undefined, boolean | undefined>(
  "approved",
  undefined,
  {
    transform: (value) => {
      if (value === undefined || value === null) return undefined;
      return typeof value === "string" ? value === "true" : value;
    },
  }
);

/**
 * 排序字段使用索引全路径，与后端 SortableRequest 的解析规则一致。
 * 默认发布时间倒序，与后端未传 sort 时的兜底排序保持一致。
 */
const sortBy = useRouteQuery<string>("sort", "spec.releaseTime,desc");

const sortItems = [
  {
    label: "发布时间倒序",
    value: "spec.releaseTime,desc",
  },
  {
    label: "发布时间升序",
    value: "spec.releaseTime,asc",
  },
  {
    label: "创建时间倒序",
    value: "metadata.creationTimestamp,desc",
  },
  {
    label: "创建时间升序",
    value: "metadata.creationTimestamp,asc",
  },
];

const page = ref(1);
const size = ref(20);
const total = ref(0);
const totalPages = ref(1);
const hasPrevious = ref(false);
const hasNext = ref(false);

const keyword = ref("");
const momentsRangeTime = ref<Array<Date>>([]);

const startDate = computed(() => {
  const date: Date = momentsRangeTime.value[0];

  if (!date) {
    return;
  }

  return utils.date.dayjs(date).startOf("day").toISOString();
});
const endDate = computed(() => {
  const endTime: Date = momentsRangeTime.value[1];

  if (!endTime) {
    return;
  }

  return utils.date.dayjs(endTime).endOf("day").toISOString();
});

const {
  data: moments,
  isLoading,
  refetch,
} = useQuery({
  queryKey: [
    "plugin:moments:list",
    page,
    size,
    selectedApprovedStatus,
    startDate,
    endDate,
    keyword,
    tag,
    sortBy,
  ],
  queryFn: async () => {
    const { data } = await momentsConsoleApiClient.moment.listMoments({
      page: page.value,
      size: size.value,
      approved: selectedApprovedStatus.value,
      keyword: keyword.value,
      startDate: startDate.value,
      endDate: endDate.value,
      tag: tag.value,
      sort: [sortBy.value],
    });

    total.value = data.total;
    totalPages.value = data.totalPages;
    hasNext.value = data.hasNext;
    hasPrevious.value = data.hasPrevious;
    return data.items;
  },
  refetchInterval: (data) => {
    const hasDeletingData = data?.some((moment) => {
      return !!moment.moment.metadata.deletionTimestamp;
    });
    return hasDeletingData ? 1000 : false;
  },
  refetchOnWindowFocus: false,
});

function updateTagQuery(tagQuery: string) {
  tag.value = tagQuery;
}

provide("tag", {
  tag: tag.value,
  updateTagQuery,
});

watch([tag, selectedApprovedStatus, momentsRangeTime, sortBy], () => {
  page.value = 1;
  size.value = 20;
});

const handleJumpToFrontDesk = () => {
  window.open("/moments", "_blank");
};

const router = useRouter();

/**
 * 未登录访客的展示范围来自插件设置，这里只做展示，不提供编辑。
 */
const { data: viewScope } = useQuery<ViewScopeVo>({
  queryKey: ["plugin:moments:view-scope"],
  queryFn: async () => {
    const { data } = await axiosInstance.get<ViewScopeVo>(
      "/apis/api.moment.halo.run/v1alpha1/view-scope"
    );
    return data;
  },
  refetchOnWindowFocus: false,
});

const handleOpenViewScopeSetting = () => {
  router.push("/plugins/PluginMoments");
};

usePluginShikiScriptLoader();
</script>
<template>
  <VPageHeader title="瞬间">
    <template #icon>
      <MingcuteMomentsLine />
    </template>
    <template #actions>
      <VButton @click="handleJumpToFrontDesk" size="sm" ghost>
        <template #icon>
          <IconExternalLinkLine />
        </template>
        跳转到前台
      </VButton>
    </template>
  </VPageHeader>
  <VCard class=":uno: m-0 flex-1 md:m-4">
    <div class=":uno: mx-auto max-w-4xl px-4 md:px-8">
      <div class=":uno: moments-content my-2 flex flex-col md:my-4 space-y-2">
        <MomentEdit />

        <div class=":uno: moment-header pb-2 pt-8">
          <div class=":uno: flex flex-col justify-between sm:flex-row space-x-2">
            <div class=":uno: left-0 mb-2 mr-2 flex items-center sm:mb-0 space-x-2">
              <TagFilterDropdown v-model="tag" :label="'标签'"></TagFilterDropdown>
              <FilterDropdown
                v-model="selectedApprovedStatus"
                label="状态"
                :items="[
                  {
                    label: '全部',
                  },
                  {
                    label: '已审核',
                    value: true,
                  },
                  {
                    label: '待审核',
                    value: false,
                  },
                ]"
              />
              <FilterDropdown v-model="sortBy" label="排序" :items="sortItems" :clearable="false" />
            </div>

            <div class=":uno: right-0 flex !ml-0">
              <DateRangeFilter v-model="momentsRangeTime" />
            </div>
          </div>
        </div>

        <div
          v-if="viewScope?.enabled && viewScope.text"
          class=":uno: flex items-center justify-between gap-3 rounded-md bg-gray-100 px-3 py-2 text-xs text-gray-500"
        >
          <span class=":uno: truncate">未登录访客：{{ viewScope.text }}</span>
          <button
            type="button"
            class=":uno: shrink-0 cursor-pointer text-gray-500 hover:text-gray-900"
            @click="handleOpenViewScopeSetting"
          >
            调整
          </button>
        </div>

        <VLoading v-if="isLoading" />

        <Transition v-else appear name="fade">
          <ul
            v-if="moments && moments.length > 0"
            class=":uno: box-border flex flex-col space-y-2"
            role="list"
          >
            <li v-for="moment in moments" :key="moment.moment.metadata.name">
              <MomentItem
                :key="moment.moment.metadata.name"
                :listed-moment="moment"
                @remove="refetch()"
              />
            </li>
          </ul>
          <template v-else>
            <div class=":uno: h-full flex items-center justify-center">
              <span class=":uno: text-gray-500">暂无数据</span>
            </div>
          </template>
        </Transition>

        <div v-if="hasPrevious || hasNext" class=":uno: my-5 flex justify-center">
          <VPagination
            v-model:page="page"
            v-model:size="size"
            class=":uno: !bg-transparent"
            :total="total"
            :size-options="[20, 30, 50, 100]"
          />
        </div>
      </div>
    </div>
  </VCard>
</template>
