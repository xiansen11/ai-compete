import { useEffect, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  FileBarChart,
  FileUp,
  FolderOpen,
  Pencil,
  PlayCircle,
  RefreshCw,
  Trash2,
} from "lucide-react";
import { toast } from "sonner";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import * as z from "zod";

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";
import {
  deleteDocument,
  enableDocument,
  getChunkLogsPage,
  getChunkStrategies,
  getDocument,
  getDocumentsPage,
  getKnowledgeBase,
  startDocumentChunk,
  updateDocument,
  uploadDocument,
  type ChunkStrategyOption,
  type KnowledgeBase,
  type KnowledgeDocument,
  type KnowledgeDocumentChunkLog,
  type KnowledgeDocumentUploadPayload,
  type PageResult,
} from "@/services/knowledgeService";
import { getIngestionPipelines, type IngestionPipeline } from "@/services/ingestionService";
import { getSystemSettings } from "@/services/settingsService";
import { getErrorMessage } from "@/utils/error";

const PAGE_SIZE = 10;

const STATUS_OPTIONS = [
  { value: "pending", label: "待处理" },
  { value: "running", label: "处理中" },
  { value: "failed", label: "失败" },
  { value: "success", label: "成功" },
];

const SOURCE_OPTIONS = [
  { value: "file", label: "本地文件" },
  { value: "url", label: "远程 URL" },
];

const PROCESS_MODE_OPTIONS = [
  { value: "chunk", label: "直接分块" },
  { value: "pipeline", label: "解析管道" },
];

const KB_TYPE_OPTIONS = [
  { value: "GUIDE", label: "办事指南库" },
  { value: "RULE", label: "赛场规则库" },
  { value: "PITFALL", label: "技术踩坑库" },
  { value: "EXEMPLAR", label: "满分作业库" },
];

type UploadFormValues = {
  sourceType: "file" | "url";
  sourceLocation: string;
  scheduleEnabled: boolean;
  scheduleCron: string;
  processMode: "chunk" | "pipeline";
  chunkStrategy: string;
  chunkSize: string;
  overlapSize: string;
  targetChars: string;
  maxChars: string;
  minChars: string;
  overlapChars: string;
  pipelineId: string;
  targetKbType: string;
  autoRoute: boolean;
};

const uploadSchema = z.object({
  sourceType: z.enum(["file", "url"]),
  sourceLocation: z.string(),
  scheduleEnabled: z.boolean(),
  scheduleCron: z.string(),
  processMode: z.enum(["chunk", "pipeline"]),
  chunkStrategy: z.string(),
  chunkSize: z.string(),
  overlapSize: z.string(),
  targetChars: z.string(),
  maxChars: z.string(),
  minChars: z.string(),
  overlapChars: z.string(),
  pipelineId: z.string(),
  targetKbType: z.string(),
  autoRoute: z.boolean(),
}).superRefine((value, ctx) => {
  if (value.sourceType === "url" && !value.sourceLocation.trim()) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      path: ["sourceLocation"],
      message: "请输入远程文档地址",
    });
  }
  if (value.sourceType === "url" && value.scheduleEnabled && !value.scheduleCron.trim()) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      path: ["scheduleCron"],
      message: "开启定时拉取时必须填写 cron 表达式",
    });
  }
  if (value.processMode === "pipeline" && !value.pipelineId) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      path: ["pipelineId"],
      message: "请选择解析管道",
    });
  }
});

function formatDate(value?: string | null) {
  if (!value) {
    return "-";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString("zh-CN");
}

function formatSize(size?: number | null) {
  if (size === null || size === undefined) {
    return "-";
  }
  if (size < 1024) {
    return `${size} B`;
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  if (size < 1024 * 1024 * 1024) {
    return `${(size / 1024 / 1024).toFixed(1)} MB`;
  }
  return `${(size / 1024 / 1024 / 1024).toFixed(1)} GB`;
}

function formatSourceLabel(sourceType?: string | null) {
  const normalized = sourceType?.toLowerCase();
  if (normalized === "url") {
    return "远程 URL";
  }
  if (normalized === "file") {
    return "本地文件";
  }
  return "-";
}

function formatChunkStrategy(strategy?: string | null) {
  const normalized = strategy?.toLowerCase();
  if (normalized === "fixed_size") {
    return "固定大小";
  }
  if (normalized === "structure_aware") {
    return "结构感知";
  }
  return strategy || "-";
}

function formatKbType(kbType?: string | null) {
  return KB_TYPE_OPTIONS.find((item) => item.value === kbType)?.label || kbType || "未标记";
}

function formatConfidence(value?: number | null) {
  if (value === null || value === undefined) {
    return "-";
  }
  return `${Math.round(value * 100)}%`;
}

function formatMetadataJson(value?: string | null) {
  if (!value) {
    return "-";
  }
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

function parseChunkConfig(raw?: string | null): Record<string, unknown> {
  if (!raw) {
    return {};
  }
  try {
    const parsed = JSON.parse(raw);
    if (parsed && typeof parsed === "object") {
      return parsed as Record<string, unknown>;
    }
    return {};
  } catch {
    return {};
  }
}

function statusDotClass(status?: string | null) {
  if (!status) {
    return "bg-muted-foreground/40";
  }
  const normalized = status.toLowerCase();
  if (normalized === "success") {
    return "bg-emerald-500";
  }
  if (normalized === "failed") {
    return "bg-red-500";
  }
  if (normalized === "running") {
    return "bg-amber-500";
  }
  if (normalized === "pending") {
    return "bg-slate-400";
  }
  return "bg-muted-foreground/40";
}

function routingBadgeClass(kbType?: string | null) {
  switch ((kbType || "").toUpperCase()) {
    case "GUIDE":
      return "border-emerald-200 bg-emerald-50 text-emerald-700";
    case "RULE":
      return "border-amber-200 bg-amber-50 text-amber-700";
    case "PITFALL":
      return "border-rose-200 bg-rose-50 text-rose-700";
    case "EXEMPLAR":
      return "border-sky-200 bg-sky-50 text-sky-700";
    default:
      return "border-slate-200 bg-slate-100 text-slate-600";
  }
}

export function KnowledgeDocumentsPage() {
  const { kbId } = useParams();
  const navigate = useNavigate();

  const [kb, setKb] = useState<KnowledgeBase | null>(null);
  const [pageData, setPageData] = useState<PageResult<KnowledgeDocument> | null>(null);
  const [current, setCurrent] = useState(1);
  const [loading, setLoading] = useState(false);
  const [statusFilter, setStatusFilter] = useState<string | undefined>();
  const [keyword, setKeyword] = useState("");
  const [searchInput, setSearchInput] = useState("");
  const [uploadOpen, setUploadOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<KnowledgeDocument | null>(null);
  const [chunkTarget, setChunkTarget] = useState<KnowledgeDocument | null>(null);
  const [detailTarget, setDetailTarget] = useState<KnowledgeDocument | null>(null);
  const [detailName, setDetailName] = useState("");
  const [detailSaving, setDetailSaving] = useState(false);
  const [detailProcessMode, setDetailProcessMode] = useState("chunk");
  const [detailChunkStrategy, setDetailChunkStrategy] = useState("structure_aware");
  const [detailPipelineId, setDetailPipelineId] = useState("");
  const [detailStrategies, setDetailStrategies] = useState<ChunkStrategyOption[]>([]);
  const [detailPipelines, setDetailPipelines] = useState<IngestionPipeline[]>([]);
  const [detailConfigValues, setDetailConfigValues] = useState<Record<string, string>>({});
  const [detailSourceLocation, setDetailSourceLocation] = useState("");
  const [detailScheduleEnabled, setDetailScheduleEnabled] = useState(false);
  const [detailScheduleCron, setDetailScheduleCron] = useState("");
  const [logTarget, setLogTarget] = useState<KnowledgeDocument | null>(null);
  const [logData, setLogData] = useState<PageResult<KnowledgeDocumentChunkLog> | null>(null);
  const [logLoading, setLogLoading] = useState(false);

  const documents = pageData?.records || [];

  const loadKnowledgeBase = async () => {
    if (!kbId) {
      return;
    }
    try {
      const data = await getKnowledgeBase(kbId);
      setKb(data);
    } catch (error) {
      toast.error(getErrorMessage(error, "加载知识库失败"));
      console.error(error);
    }
  };

  const loadDocuments = async (page = current, status = statusFilter, keywordValue = keyword) => {
    if (!kbId) {
      return;
    }
    setLoading(true);
    try {
      const data = await getDocumentsPage(kbId, {
        current: page,
        size: PAGE_SIZE,
        status,
        keyword: keywordValue || undefined,
      });
      setPageData(data);
    } catch (error) {
      toast.error(getErrorMessage(error, "加载文档失败"));
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadKnowledgeBase();
  }, [kbId]);

  useEffect(() => {
    loadDocuments();
  }, [kbId, current, statusFilter, keyword]);

  useEffect(() => {
    if (!detailTarget) {
      setDetailName("");
      setDetailProcessMode("chunk");
      setDetailChunkStrategy("structure_aware");
      setDetailPipelineId("");
      setDetailConfigValues({});
      setDetailStrategies([]);
      setDetailPipelines([]);
      setDetailSourceLocation("");
      setDetailScheduleEnabled(false);
      setDetailScheduleCron("");
      return;
    }

    setDetailName(detailTarget.docName || "");
    setDetailProcessMode((detailTarget.processMode || "chunk").toLowerCase());
    setDetailChunkStrategy((detailTarget.chunkStrategy || "structure_aware").toLowerCase());
    setDetailPipelineId(detailTarget.pipelineId ? String(detailTarget.pipelineId) : "");
    setDetailSourceLocation(detailTarget.sourceLocation || "");
    setDetailScheduleEnabled(Boolean(detailTarget.scheduleEnabled));
    setDetailScheduleCron(detailTarget.scheduleCron || "");

    const config = parseChunkConfig(detailTarget.chunkConfig);
    const values: Record<string, string> = {};
    for (const [key, value] of Object.entries(config)) {
      values[key] = String(value);
    }
    setDetailConfigValues(values);

    getChunkStrategies().then(setDetailStrategies).catch(() => {});
    getIngestionPipelines(1, 100).then((result) => setDetailPipelines(result.records || [])).catch(() => {});
  }, [detailTarget]);

  const handleSearch = () => {
    setCurrent(1);
    setKeyword(searchInput.trim());
  };

  const handleRefresh = () => {
    setCurrent(1);
    loadDocuments(1, statusFilter, keyword);
  };

  const handleDelete = async () => {
    if (!deleteTarget) {
      return;
    }
    try {
      await deleteDocument(String(deleteTarget.id));
      toast.success("文档已删除");
      setDeleteTarget(null);
      setCurrent(1);
      await loadDocuments(1, statusFilter, keyword);
    } catch (error) {
      toast.error(getErrorMessage(error, "删除文档失败"));
      console.error(error);
    }
  };

  const handleChunk = async () => {
    if (!chunkTarget) {
      return;
    }
    try {
      await startDocumentChunk(String(chunkTarget.id));
      toast.success("已开始分块处理");
      setChunkTarget(null);
      await loadDocuments(current, statusFilter, keyword);
    } catch (error) {
      toast.error(getErrorMessage(error, "启动分块失败"));
      console.error(error);
    }
  };

  const handleToggleEnabled = async (doc: KnowledgeDocument) => {
    const enabled = Boolean(doc.enabled);
    try {
      await enableDocument(String(doc.id), !enabled);
      toast.success(!enabled ? "文档已启用" : "文档已禁用");
      await loadDocuments(current, statusFilter, keyword);
    } catch (error) {
      toast.error(getErrorMessage(error, "更新文档状态失败"));
      console.error(error);
    }
  };

  const handleDetailSave = async () => {
    if (!detailTarget) {
      return;
    }
    const nextName = detailName.trim();
    if (!nextName) {
      toast.error("文档名称不能为空");
      return;
    }

    setDetailSaving(true);
    try {
      const payload: Parameters<typeof updateDocument>[1] = {
        docName: nextName,
        processMode: detailProcessMode,
      };
      if (detailProcessMode === "chunk") {
        payload.chunkStrategy = detailChunkStrategy;
        const strategy = detailStrategies.find((item) => item.value === detailChunkStrategy);
        if (strategy) {
          const configObj: Record<string, number> = {};
          for (const key of Object.keys(strategy.defaultConfig)) {
            configObj[key] = Number(detailConfigValues[key]) || strategy.defaultConfig[key];
          }
          payload.chunkConfig = JSON.stringify(configObj);
        }
      } else {
        payload.pipelineId = detailPipelineId;
      }

      if (detailTarget.sourceType?.toLowerCase() === "url") {
        payload.sourceLocation = detailSourceLocation.trim();
        payload.scheduleEnabled = detailScheduleEnabled ? 1 : 0;
        payload.scheduleCron = detailScheduleCron.trim();
      }

      await updateDocument(String(detailTarget.id), payload);
      toast.success("文档配置已更新");
      await loadDocuments(current, statusFilter, keyword);
      setDetailTarget(null);
    } catch (error) {
      toast.error(getErrorMessage(error, "更新文档失败"));
      console.error(error);
    } finally {
      setDetailSaving(false);
    }
  };

  const loadChunkLogs = async (docId: string) => {
    setLogLoading(true);
    try {
      const data = await getChunkLogsPage(docId, 1, 10);
      setLogData(data);
    } catch (error) {
      toast.error(getErrorMessage(error, "加载分块日志失败"));
      console.error(error);
    } finally {
      setLogLoading(false);
    }
  };

  const handleOpenChunkLogs = (doc: KnowledgeDocument) => {
    setLogTarget(doc);
    loadChunkLogs(String(doc.id));
  };

  const formatDuration = (ms?: number | null) => {
    if (ms === null || ms === undefined) {
      return "-";
    }
    if (ms < 1000) {
      return `${ms}ms`;
    }
    return `${(ms / 1000).toFixed(2)}s`;
  };

  const detailSourceType = detailTarget?.sourceType?.toLowerCase();
  const detailIsUrlSource = detailSourceType === "url";

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">文档管理</h1>
          <p className="admin-page-subtitle">
            {kb ? `${kb.name} / ${kb.collectionName || "-"} / ${formatKbType(kb.kbType)}` : kbId}
          </p>
        </div>
        <div className="admin-page-actions">
          <Button variant="outline" onClick={() => navigate("/admin/knowledge")}>
            返回知识库
          </Button>
          <Button className="admin-primary-gradient" onClick={() => setUploadOpen(true)}>
            <FileUp className="mr-2 h-4 w-4" />
            上传文档
          </Button>
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-4">
        <Card>
          <CardContent className="pt-6">
            <div className="flex items-center gap-3">
              <FolderOpen className="h-5 w-5 text-muted-foreground" />
              <div>
                <div className="text-xs text-muted-foreground">当前知识库</div>
                <div className="text-sm font-medium">{kb?.name || "-"}</div>
              </div>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-6">
            <div className="flex items-center gap-3">
              <FileBarChart className="h-5 w-5 text-muted-foreground" />
              <div>
                <div className="text-xs text-muted-foreground">文档数量</div>
                <div className="text-sm font-medium">{pageData?.total ?? 0}</div>
              </div>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-6">
            <div className="flex items-center gap-3">
              <Badge variant="outline" className={cn("px-3 py-1", routingBadgeClass(kb?.kbType))}>
                {formatKbType(kb?.kbType)}
              </Badge>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-6">
            <div className="text-xs text-muted-foreground">默认 Profile</div>
            <div className="mt-1 text-sm font-medium">{kb?.defaultPipelineProfile || "-"}</div>
          </CardContent>
        </Card>
      </div>

      <Card className="mt-4">
        <CardHeader>
          <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
            <div>
              <CardTitle>文档列表</CardTitle>
              <CardDescription>支持按状态筛选，并显示路由结果与待复核状态。</CardDescription>
            </div>
            <div className="flex flex-1 flex-wrap items-center justify-end gap-2">
              <Input
                value={searchInput}
                onChange={(event) => setSearchInput(event.target.value)}
                placeholder="搜索文档名称"
                className="max-w-xs"
              />
              <Button variant="outline" onClick={handleSearch}>
                搜索
              </Button>
              <Select
                value={statusFilter || "all"}
                onValueChange={(value) => {
                  setCurrent(1);
                  setStatusFilter(value === "all" ? undefined : value);
                }}
              >
                <SelectTrigger className="w-[160px]">
                  <SelectValue placeholder="处理状态" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">全部状态</SelectItem>
                  {STATUS_OPTIONS.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Button variant="outline" onClick={handleRefresh}>
                <RefreshCw className="mr-2 h-4 w-4" />
                刷新
              </Button>
            </div>
          </div>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="py-8 text-center text-muted-foreground">加载中...</div>
          ) : documents.length === 0 ? (
            <div className="py-8 text-center text-muted-foreground">暂无文档。</div>
          ) : (
            <Table className="min-w-[1440px]">
              <TableHeader>
                <TableRow>
                  <TableHead className="w-[240px]">文档</TableHead>
                  <TableHead className="w-[110px]">来源</TableHead>
                  <TableHead className="w-[110px]">处理模式</TableHead>
                  <TableHead className="w-[110px]">路由结果</TableHead>
                  <TableHead className="w-[110px]">置信度</TableHead>
                  <TableHead className="w-[120px]">待复核</TableHead>
                  <TableHead className="w-[110px]">状态</TableHead>
                  <TableHead className="w-[80px]">启用</TableHead>
                  <TableHead className="w-[80px]">分块</TableHead>
                  <TableHead className="w-[80px]">类型</TableHead>
                  <TableHead className="w-[90px]">大小</TableHead>
                  <TableHead className="w-[170px]">更新时间</TableHead>
                  <TableHead className="w-[220px]">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {documents.map((doc) => {
                  const enabled = Boolean(doc.enabled);
                  return (
                    <TableRow key={doc.id}>
                      <TableCell className="font-medium">
                        <div className="space-y-1">
                          <button
                            type="button"
                            className="admin-link max-w-[220px] truncate text-left"
                            onClick={async () => {
                              try {
                                const detail = await getDocument(String(doc.id));
                                setDetailTarget(detail);
                              } catch (error) {
                                toast.error(getErrorMessage(error, "加载文档详情失败"));
                              }
                            }}
                          >
                            {doc.docName || "-"}
                          </button>
                          {doc.routingReason ? (
                            <p className="line-clamp-2 text-xs text-muted-foreground">{doc.routingReason}</p>
                          ) : null}
                        </div>
                      </TableCell>
                      <TableCell>{formatSourceLabel(doc.sourceType)}</TableCell>
                      <TableCell>
                        <div className="space-y-1 text-xs text-muted-foreground">
                          <div>{doc.processMode || "-"}</div>
                          {doc.processMode === "chunk" ? (
                            <div>{formatChunkStrategy(doc.chunkStrategy)}</div>
                          ) : (
                            <div>{doc.pipelineId ? `Pipeline ${doc.pipelineId}` : "-"}</div>
                          )}
                        </div>
                      </TableCell>
                      <TableCell>
                        <Badge variant="outline" className={cn("px-3 py-1", routingBadgeClass(doc.routedKbType))}>
                          {formatKbType(doc.routedKbType)}
                        </Badge>
                      </TableCell>
                      <TableCell>{formatConfidence(doc.routingConfidence)}</TableCell>
                      <TableCell>
                        {doc.needsReview ? (
                          <Badge variant="outline" className="border-red-200 bg-red-50 text-red-700">
                            需要复核
                          </Badge>
                        ) : (
                          <Badge variant="outline" className="border-emerald-200 bg-emerald-50 text-emerald-700">
                            已确认
                          </Badge>
                        )}
                      </TableCell>
                      <TableCell>
                        <div className="inline-flex items-center gap-2 text-xs text-muted-foreground">
                          <span className={cn("h-2 w-2 rounded-full", statusDotClass(doc.status))} />
                          <span>{doc.status || "-"}</span>
                        </div>
                      </TableCell>
                      <TableCell>
                        <button
                          type="button"
                          role="switch"
                          aria-checked={enabled}
                          aria-label={enabled ? "已启用，点击禁用" : "已禁用，点击启用"}
                          onClick={() => handleToggleEnabled(doc)}
                          className={cn(
                            "relative inline-flex h-5 w-9 items-center rounded-full transition-colors",
                            enabled ? "bg-blue-600" : "bg-slate-200"
                          )}
                        >
                          <span
                            className={cn(
                              "inline-block h-4 w-4 transform rounded-full bg-background shadow transition-transform",
                              enabled ? "translate-x-4" : "translate-x-1"
                            )}
                          />
                        </button>
                      </TableCell>
                      <TableCell>{doc.chunkCount ?? 0}</TableCell>
                      <TableCell>{doc.fileType || "-"}</TableCell>
                      <TableCell>{formatSize(doc.fileSize)}</TableCell>
                      <TableCell>{formatDate(doc.updateTime)}</TableCell>
                      <TableCell>
                        <div className="flex flex-wrap gap-2">
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={async () => {
                              try {
                                const detail = await getDocument(String(doc.id));
                                setDetailTarget(detail);
                              } catch (error) {
                                toast.error(getErrorMessage(error, "加载文档详情失败"));
                              }
                            }}
                          >
                            <Pencil className="mr-1 h-4 w-4" />
                            编辑
                          </Button>
                          <Button variant="outline" size="sm" onClick={() => setChunkTarget(doc)}>
                            <PlayCircle className="mr-1 h-4 w-4" />
                            分块
                          </Button>
                          <Button variant="outline" size="sm" onClick={() => handleOpenChunkLogs(doc)}>
                            日志
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            className="text-destructive hover:text-destructive"
                            onClick={() => setDeleteTarget(doc)}
                          >
                            <Trash2 className="mr-1 h-4 w-4" />
                            删除
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      <UploadDialog
        open={uploadOpen}
        onOpenChange={setUploadOpen}
        onSubmit={async (payload) => {
          if (!kbId) {
            return;
          }
          const created = await uploadDocument(kbId, payload);
          const finalKbType = created.routedKbType ? formatKbType(created.routedKbType) : "未返回";
          const reviewTip = created.needsReview ? "，已标记为待复核" : "";
          toast.success(`上传成功，路由到 ${finalKbType}${reviewTip}`);
          setUploadOpen(false);
          setCurrent(1);
          await loadKnowledgeBase();
          await loadDocuments(1, statusFilter, keyword);
        }}
      />

      <AlertDialog open={!!deleteTarget} onOpenChange={() => setDeleteTarget(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>确认删除文档？</AlertDialogTitle>
            <AlertDialogDescription>删除后不可恢复，请确认当前文档不再需要。</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>取消</AlertDialogCancel>
            <AlertDialogAction onClick={handleDelete} className="bg-destructive text-destructive-foreground">
              删除
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <AlertDialog open={!!chunkTarget} onOpenChange={() => setChunkTarget(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{chunkTarget?.chunkCount ? "重新分块？" : "开始分块？"}</AlertDialogTitle>
            <AlertDialogDescription>
              将根据当前路由结果、解析模式和分块策略生成新的向量内容。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>取消</AlertDialogCancel>
            <AlertDialogAction onClick={handleChunk}>确认</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <Dialog open={!!detailTarget} onOpenChange={(open) => !open && setDetailTarget(null)}>
        <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-[860px]">
          <DialogHeader>
            <DialogTitle>编辑文档</DialogTitle>
            <DialogDescription>可调整文档名称、分块策略和 URL 调度信息。</DialogDescription>
          </DialogHeader>
          {detailTarget ? (
            <div className="space-y-5">
              <div className="grid gap-4 md:grid-cols-2">
                <div className="space-y-2">
                  <label className="text-sm font-medium">文档名称</label>
                  <Input value={detailName} onChange={(event) => setDetailName(event.target.value)} />
                </div>
                <div className="space-y-2">
                  <label className="text-sm font-medium">路由结果</label>
                  <div className="flex items-center gap-2">
                    <Badge variant="outline" className={cn("px-3 py-1", routingBadgeClass(detailTarget.routedKbType))}>
                      {formatKbType(detailTarget.routedKbType)}
                    </Badge>
                    <span className="text-sm text-muted-foreground">
                      置信度 {formatConfidence(detailTarget.routingConfidence)}
                    </span>
                  </div>
                </div>
              </div>

              <div className="grid gap-4 md:grid-cols-3">
                <div className="rounded-xl border p-4">
                  <div className="text-xs text-muted-foreground">文件类型</div>
                  <div className="mt-1 text-sm font-medium">{detailTarget.fileType || "-"}</div>
                </div>
                <div className="rounded-xl border p-4">
                  <div className="text-xs text-muted-foreground">处理模式</div>
                  <Select value={detailProcessMode} onValueChange={setDetailProcessMode}>
                    <SelectTrigger className="mt-2">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {PROCESS_MODE_OPTIONS.map((option) => (
                        <SelectItem key={option.value} value={option.value}>
                          {option.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                <div className="rounded-xl border p-4">
                  <div className="text-xs text-muted-foreground">待复核</div>
                  <div className="mt-2">
                    {detailTarget.needsReview ? (
                      <Badge variant="outline" className="border-red-200 bg-red-50 text-red-700">
                        需要复核
                      </Badge>
                    ) : (
                      <Badge variant="outline" className="border-emerald-200 bg-emerald-50 text-emerald-700">
                        已确认
                      </Badge>
                    )}
                  </div>
                </div>
              </div>

              {detailProcessMode === "chunk" ? (
                <div className="space-y-4 rounded-2xl border p-4">
                  <div className="space-y-2">
                    <label className="text-sm font-medium">分块策略</label>
                    <Select
                      value={detailChunkStrategy}
                      onValueChange={(value) => {
                        setDetailChunkStrategy(value);
                        const strategy = detailStrategies.find((item) => item.value === value);
                        if (strategy) {
                          const values: Record<string, string> = {};
                          Object.entries(strategy.defaultConfig).forEach(([key, val]) => {
                            values[key] = String(val);
                          });
                          setDetailConfigValues(values);
                        }
                      }}
                    >
                      <SelectTrigger>
                        <SelectValue placeholder="选择分块策略" />
                      </SelectTrigger>
                      <SelectContent>
                        {detailStrategies.map((item) => (
                          <SelectItem key={item.value} value={item.value}>
                            {item.label}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                  <div className="grid gap-4 md:grid-cols-2">
                    {Object.keys(detailConfigValues).length > 0 ? (
                      Object.entries(detailConfigValues).map(([key, value]) => (
                        <div key={key} className="space-y-2">
                          <label className="text-sm font-medium">{key}</label>
                          <Input
                            value={value}
                            onChange={(event) =>
                              setDetailConfigValues((prev) => ({ ...prev, [key]: event.target.value }))
                            }
                          />
                        </div>
                      ))
                    ) : (
                      <div className="text-sm text-muted-foreground">当前策略暂无可编辑参数。</div>
                    )}
                  </div>
                </div>
              ) : (
                <div className="space-y-2 rounded-2xl border p-4">
                  <label className="text-sm font-medium">解析管道</label>
                  <Select value={detailPipelineId} onValueChange={setDetailPipelineId}>
                    <SelectTrigger>
                      <SelectValue placeholder="选择解析管道" />
                    </SelectTrigger>
                    <SelectContent>
                      {detailPipelines.map((pipeline) => (
                        <SelectItem key={pipeline.id} value={pipeline.id}>
                          {pipeline.name}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              )}

              {detailIsUrlSource ? (
                <div className="space-y-4 rounded-2xl border p-4">
                  <div className="space-y-2">
                    <label className="text-sm font-medium">远程地址</label>
                    <Input
                      value={detailSourceLocation}
                      onChange={(event) => setDetailSourceLocation(event.target.value)}
                    />
                  </div>
                  <div className="flex items-center justify-between rounded-xl border px-4 py-3">
                    <div>
                      <div className="text-sm font-medium">开启定时拉取</div>
                      <div className="text-xs text-muted-foreground">适用于官网通知、远程 FAQ 等周期同步场景。</div>
                    </div>
                    <Checkbox
                      checked={detailScheduleEnabled}
                      onCheckedChange={(checked) => setDetailScheduleEnabled(Boolean(checked))}
                    />
                  </div>
                  {detailScheduleEnabled ? (
                    <div className="space-y-2">
                      <label className="text-sm font-medium">Cron 表达式</label>
                      <Input
                        value={detailScheduleCron}
                        onChange={(event) => setDetailScheduleCron(event.target.value)}
                        placeholder="例如：0 0 * * ?"
                      />
                    </div>
                  ) : null}
                </div>
              ) : null}

              <div className="space-y-2">
                <div className="text-sm font-medium">路由说明</div>
                <Textarea value={detailTarget.routingReason || "-"} readOnly className="min-h-[72px]" />
              </div>
              <div className="space-y-2">
                <div className="text-sm font-medium">提取出的 Metadata</div>
                <Textarea
                  value={formatMetadataJson(detailTarget.extractedMetadataJson)}
                  readOnly
                  className="min-h-[180px] font-mono text-xs"
                />
              </div>
            </div>
          ) : null}
          <DialogFooter>
            <Button variant="outline" onClick={() => setDetailTarget(null)} disabled={detailSaving}>
              取消
            </Button>
            <Button onClick={handleDetailSave} disabled={detailSaving}>
              {detailSaving ? "保存中..." : "保存"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={!!logTarget} onOpenChange={(open) => !open && setLogTarget(null)}>
        <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-[900px]">
          <DialogHeader>
            <DialogTitle>分块详情</DialogTitle>
            <DialogDescription>{logTarget?.docName || "当前文档"} 的最新分块执行日志</DialogDescription>
          </DialogHeader>
          {logLoading ? (
            <div className="py-8 text-center text-muted-foreground">加载中...</div>
          ) : logData?.records?.length ? (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>状态</TableHead>
                  <TableHead>模式</TableHead>
                  <TableHead>分块数</TableHead>
                  <TableHead>提取耗时</TableHead>
                  <TableHead>分块耗时</TableHead>
                  <TableHead>向量耗时</TableHead>
                  <TableHead>总耗时</TableHead>
                  <TableHead>开始时间</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {logData.records.map((log) => (
                  <TableRow key={log.id}>
                    <TableCell>{log.status}</TableCell>
                    <TableCell>{log.processMode || "-"}</TableCell>
                    <TableCell>{log.chunkCount ?? "-"}</TableCell>
                    <TableCell>{formatDuration(log.extractDuration)}</TableCell>
                    <TableCell>{formatDuration(log.chunkDuration)}</TableCell>
                    <TableCell>{formatDuration(log.embedDuration)}</TableCell>
                    <TableCell>{formatDuration(log.totalDuration)}</TableCell>
                    <TableCell>{formatDate(log.startTime)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          ) : (
            <div className="py-8 text-center text-muted-foreground">暂无分块日志。</div>
          )}
        </DialogContent>
      </Dialog>

      {pageData ? (
        <div className="mt-4 flex flex-wrap items-center justify-between gap-2 text-sm text-slate-500">
          <span>共 {pageData.total} 条</span>
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => setCurrent((prev) => Math.max(1, prev - 1))}
              disabled={pageData.current <= 1}
            >
              上一页
            </Button>
            <span>
              {pageData.current} / {pageData.pages}
            </span>
            <Button
              variant="outline"
              size="sm"
              onClick={() => setCurrent((prev) => Math.min(pageData.pages || 1, prev + 1))}
              disabled={pageData.current >= pageData.pages}
            >
              下一页
            </Button>
          </div>
        </div>
      ) : null}
    </div>
  );
}

interface UploadDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSubmit: (payload: KnowledgeDocumentUploadPayload) => Promise<void>;
}

function UploadDialog({ open, onOpenChange, onSubmit }: UploadDialogProps) {
  const [saving, setSaving] = useState(false);
  const [file, setFile] = useState<File | null>(null);
  const [maxFileSize, setMaxFileSize] = useState(100 * 1024 * 1024);
  const [chunkStrategies, setChunkStrategies] = useState<ChunkStrategyOption[]>([]);
  const [pipelines, setPipelines] = useState<IngestionPipeline[]>([]);
  const [loadingPipelines, setLoadingPipelines] = useState(false);

  const form = useForm<any>({
    resolver: zodResolver(uploadSchema),
    defaultValues: {
      sourceType: "file",
      sourceLocation: "",
      scheduleEnabled: false,
      scheduleCron: "",
      processMode: "chunk",
      chunkStrategy: "structure_aware",
      chunkSize: "",
      overlapSize: "",
      targetChars: "",
      maxChars: "",
      minChars: "",
      overlapChars: "",
      pipelineId: "",
      targetKbType: "AUTO",
      autoRoute: true,
    },
  });

  const sourceType = form.watch("sourceType");
  const processMode = form.watch("processMode");
  const chunkStrategy = form.watch("chunkStrategy");
  const scheduleEnabled = form.watch("scheduleEnabled");
  const autoRoute = form.watch("autoRoute");

  const isUrlSource = sourceType === "url";
  const isChunkMode = processMode === "chunk";
  const isPipelineMode = processMode === "pipeline";

  useEffect(() => {
    if (!open) {
      return;
    }
    getSystemSettings()
      .then((settings) => {
        setMaxFileSize(settings.upload?.maxFileSize || 100 * 1024 * 1024);
      })
      .catch(() => {});
    getChunkStrategies().then(setChunkStrategies).catch(() => {});
    setLoadingPipelines(true);
    getIngestionPipelines(1, 100)
      .then((result) => setPipelines(result.records || []))
      .catch(() => {})
      .finally(() => setLoadingPipelines(false));
  }, [open]);

  useEffect(() => {
    if (!open) {
      form.reset();
      setFile(null);
    }
  }, [open, form]);

  const selectedStrategy = useMemo(
    () => chunkStrategies.find((item) => item.value === chunkStrategy),
    [chunkStrategies, chunkStrategy]
  );

  useEffect(() => {
    if (!selectedStrategy) {
      return;
    }
    Object.entries(selectedStrategy.defaultConfig).forEach(([key, value]) => {
      const currentValue = form.getValues(key as keyof UploadFormValues);
      if (!currentValue) {
        form.setValue(key as keyof UploadFormValues, String(value));
      }
    });
  }, [selectedStrategy, form]);

  const parseNumber = (value?: string) => {
    if (!value || !value.trim()) {
      return null;
    }
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : null;
  };

  const handleSubmit = async (values: UploadFormValues) => {
    if (values.sourceType === "file" && !file) {
      toast.error("请选择上传文件");
      return;
    }
    if (values.sourceType === "file" && file && file.size > maxFileSize) {
      const sizeMB = Math.floor(maxFileSize / 1024 / 1024);
      toast.error(`上传文件大小超过限制，最大允许 ${sizeMB}MB`);
      return;
    }

    let chunkConfig: string | null = null;
    if (values.processMode === "chunk" && selectedStrategy) {
      const config: Record<string, number> = {};
      Object.keys(selectedStrategy.defaultConfig).forEach((key) => {
        const raw = values[key as keyof UploadFormValues];
        const parsed = parseNumber(typeof raw === "string" ? raw : "");
        if (parsed !== null) {
          config[key] = parsed;
        }
      });
      chunkConfig = JSON.stringify(config);
    }

    setSaving(true);
    try {
      const payload: KnowledgeDocumentUploadPayload = {
        sourceType: values.sourceType,
        file: values.sourceType === "file" ? file : null,
        sourceLocation: values.sourceType === "url" ? values.sourceLocation.trim() : null,
        scheduleEnabled: values.sourceType === "url" ? values.scheduleEnabled : false,
        scheduleCron:
          values.sourceType === "url" && values.scheduleEnabled ? values.scheduleCron.trim() : null,
        processMode: values.processMode,
        chunkStrategy: values.processMode === "chunk" ? values.chunkStrategy : undefined,
        chunkConfig,
        pipelineId: values.processMode === "pipeline" ? values.pipelineId : null,
        targetKbType: values.targetKbType === "AUTO" ? null : values.targetKbType,
        autoRoute: values.autoRoute,
      };
      await onSubmit(payload);
    } catch (error) {
      toast.error(getErrorMessage(error, "上传文档失败"));
      console.error(error);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-[720px]">
        <DialogHeader>
          <DialogTitle>上传文档</DialogTitle>
          <DialogDescription>
            上传后将先按内容进行路由分类，再按“库类型 + 文件类型”选择解析与分块策略。
          </DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form className="space-y-5" onSubmit={form.handleSubmit(handleSubmit)}>
            <div className="grid gap-4 md:grid-cols-2">
              <FormField
                control={form.control}
                name="sourceType"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>来源类型</FormLabel>
                    <Select value={field.value} onValueChange={field.onChange}>
                      <FormControl>
                        <SelectTrigger>
                          <SelectValue placeholder="选择来源类型" />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        {SOURCE_OPTIONS.map((option) => (
                          <SelectItem key={option.value} value={option.value}>
                            {option.label}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name="processMode"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>处理模式</FormLabel>
                    <Select value={field.value} onValueChange={field.onChange}>
                      <FormControl>
                        <SelectTrigger>
                          <SelectValue placeholder="选择处理模式" />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        {PROCESS_MODE_OPTIONS.map((option) => (
                          <SelectItem key={option.value} value={option.value}>
                            {option.label}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>

            {isUrlSource ? (
              <FormField
                control={form.control}
                name="sourceLocation"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>远程地址</FormLabel>
                    <FormControl>
                      <Input placeholder="https://example.com/notice.pdf" {...field} />
                    </FormControl>
                    <FormDescription>适用于官网通知、FAQ 表、远程 Markdown 等文档。</FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />
            ) : (
              <FormItem>
                <FormLabel>本地文件</FormLabel>
                <FormControl>
                  <div className="rounded-2xl border-2 border-dashed p-6">
                    <input
                      type="file"
                      onChange={(event) => setFile(event.target.files?.[0] || null)}
                      className="w-full text-sm"
                    />
                    <div className="mt-3 text-xs text-muted-foreground">
                      当前文件：{file ? `${file.name} (${formatSize(file.size)})` : "未选择"}
                    </div>
                  </div>
                </FormControl>
              </FormItem>
            )}

            {isUrlSource ? (
              <div className="space-y-3 rounded-2xl border p-4">
                <FormField
                  control={form.control}
                  name="scheduleEnabled"
                  render={({ field }) => (
                    <FormItem className="flex items-center justify-between">
                      <div>
                        <FormLabel>开启定时拉取</FormLabel>
                        <FormDescription>适合周期同步官网公告或远程文档。</FormDescription>
                      </div>
                      <FormControl>
                        <Checkbox checked={field.value} onCheckedChange={(value) => field.onChange(Boolean(value))} />
                      </FormControl>
                    </FormItem>
                  )}
                />
                {scheduleEnabled ? (
                  <FormField
                    control={form.control}
                    name="scheduleCron"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Cron 表达式</FormLabel>
                        <FormControl>
                          <Input placeholder="例如：0 0 * * ?" {...field} />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                ) : null}
              </div>
            ) : null}

            <div className="space-y-4 rounded-2xl border p-4">
              <div className="grid gap-4 md:grid-cols-2">
                <FormField
                  control={form.control}
                  name="autoRoute"
                  render={({ field }) => (
                    <FormItem className="flex items-center justify-between rounded-xl border px-4 py-3">
                      <div>
                        <FormLabel>自动路由</FormLabel>
                        <FormDescription>根据文件名、正文抽样、标题和注释自动判断目标库。</FormDescription>
                      </div>
                      <FormControl>
                        <Checkbox checked={field.value} onCheckedChange={(value) => field.onChange(Boolean(value))} />
                      </FormControl>
                    </FormItem>
                  )}
                />

                <FormField
                  control={form.control}
                  name="targetKbType"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>目标知识库</FormLabel>
                      <Select
                        value={field.value}
                        onValueChange={field.onChange}
                        disabled={autoRoute}
                      >
                        <FormControl>
                          <SelectTrigger>
                            <SelectValue placeholder="自动判断或手动指定" />
                          </SelectTrigger>
                        </FormControl>
                        <SelectContent>
                          <SelectItem value="AUTO">自动判断</SelectItem>
                          {KB_TYPE_OPTIONS.map((item) => (
                            <SelectItem key={item.value} value={item.value}>
                              {item.label}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                      <FormDescription>
                        自动路由开启时优先走分类器；关闭后按管理员指定结果入库。
                      </FormDescription>
                    </FormItem>
                  )}
                />
              </div>

              {isPipelineMode ? (
                <FormField
                  control={form.control}
                  name="pipelineId"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>解析管道</FormLabel>
                      <Select value={field.value} onValueChange={field.onChange} disabled={loadingPipelines}>
                        <FormControl>
                          <SelectTrigger>
                            <SelectValue placeholder={loadingPipelines ? "加载中..." : "请选择解析管道"} />
                          </SelectTrigger>
                        </FormControl>
                        <SelectContent>
                          {pipelines.map((pipeline) => (
                            <SelectItem key={pipeline.id} value={pipeline.id}>
                              {pipeline.name}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              ) : null}

              {isChunkMode ? (
                <div className="space-y-4">
                  <FormField
                    control={form.control}
                    name="chunkStrategy"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>分块策略</FormLabel>
                        <Select value={field.value} onValueChange={field.onChange}>
                          <FormControl>
                            <SelectTrigger>
                              <SelectValue placeholder="选择分块策略" />
                            </SelectTrigger>
                          </FormControl>
                          <SelectContent>
                            {chunkStrategies.map((option) => (
                              <SelectItem key={option.value} value={option.value}>
                                {option.label}
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                        <FormDescription>
                          解析策略最终仍会结合目标库类型和文件类型在后端进行兜底修正。
                        </FormDescription>
                      </FormItem>
                    )}
                  />

                  {selectedStrategy ? (
                    <div className="grid gap-4 md:grid-cols-2">
                      {Object.keys(selectedStrategy.defaultConfig).map((key) => {
                        const fieldName = key as keyof UploadFormValues;
                        return (
                          <div key={key} className="space-y-2">
                            <label className="text-sm font-medium">{key}</label>
                            <Input
                              type="number"
                              value={String(form.watch(fieldName) ?? "")}
                              onChange={(event) => form.setValue(fieldName, event.target.value)}
                            />
                          </div>
                        );
                      })}
                    </div>
                  ) : null}
                </div>
              ) : null}
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={saving}>
                取消
              </Button>
              <Button type="submit" disabled={saving}>
                {saving ? "上传中..." : "上传"}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
