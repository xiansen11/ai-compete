import { useCallback, useEffect, useRef, useState } from "react";
import {
  Database,
  FileBarChart,
  FolderOpen,
  Layers,
  Pencil,
  Plus,
  RefreshCw,
  Trash2,
} from "lucide-react";
import { toast } from "sonner";
import { useNavigate, useSearchParams } from "react-router-dom";

import { CreateKnowledgeBaseDialog } from "@/components/admin/CreateKnowledgeBaseDialog";
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
import { Card, CardContent } from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
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
  deleteKnowledgeBase,
  getKnowledgeBasesPage,
  renameKnowledgeBase,
  type KnowledgeBase,
  type PageResult,
} from "@/services/knowledgeService";
import { getErrorMessage } from "@/utils/error";

const PAGE_SIZE = 10;
const STATS_PAGE_SIZE = 200;

const KB_TYPE_LABELS: Record<string, string> = {
  GUIDE: "办事指南库",
  RULE: "赛场规则库",
  PITFALL: "技术踩坑库",
  EXEMPLAR: "满分作业库",
};

function formatDate(value?: string) {
  if (!value) {
    return "-";
  }
  return new Date(value).toLocaleString("zh-CN");
}

function formatJsonPreview(value?: string | null) {
  if (!value) {
    return "-";
  }
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

function getKbTypeBadgeClass(kbType?: string | null) {
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

function getCollectionBadgeClass(name?: string | null) {
  const value = (name || "").toLowerCase();
  if (value.includes("guide")) {
    return "border-emerald-200 bg-emerald-50 text-emerald-700";
  }
  if (value.includes("rule")) {
    return "border-amber-200 bg-amber-50 text-amber-700";
  }
  if (value.includes("pitfall")) {
    return "border-rose-200 bg-rose-50 text-rose-700";
  }
  if (value.includes("exemplar")) {
    return "border-sky-200 bg-sky-50 text-sky-700";
  }
  return "border-slate-200 bg-slate-100 text-slate-600";
}

export function KnowledgeListPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const nameFromQuery = searchParams.get("name") || "";

  const [pageData, setPageData] = useState<PageResult<KnowledgeBase> | null>(null);
  const [loading, setLoading] = useState(true);
  const [deleteTarget, setDeleteTarget] = useState<KnowledgeBase | null>(null);
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [searchName, setSearchName] = useState(nameFromQuery);
  const [keyword, setKeyword] = useState(nameFromQuery);
  const [pageNo, setPageNo] = useState(1);
  const [renameDialog, setRenameDialog] = useState<{ open: boolean; kb: KnowledgeBase | null }>({
    open: false,
    kb: null,
  });
  const [renameValue, setRenameValue] = useState("");
  const [detailTarget, setDetailTarget] = useState<KnowledgeBase | null>(null);
  const [stats, setStats] = useState({
    totalCount: 0,
    documentCount: 0,
    activeCount: 0,
    creatorCount: 0,
  });
  const [statsLoading, setStatsLoading] = useState(true);
  const statsRequestId = useRef(0);

  const knowledgeBases = pageData?.records || [];

  const loadKnowledgeBases = async (current = pageNo, name = keyword) => {
    try {
      setLoading(true);
      const data = await getKnowledgeBasesPage(current, PAGE_SIZE, name || undefined);
      setPageData(data);
    } catch (error) {
      toast.error(getErrorMessage(error, "加载知识库列表失败"));
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  const loadStats = useCallback(async (name = keyword) => {
    const requestId = ++statsRequestId.current;
    const normalized = name.trim();
    setStatsLoading(true);
    try {
      const firstPage = await getKnowledgeBasesPage(1, STATS_PAGE_SIZE, normalized || undefined);
      if (statsRequestId.current !== requestId) {
        return;
      }

      let documentTotal = 0;
      let activeTotal = 0;
      const creatorNames = new Set<string>();

      const addRecords = (records: KnowledgeBase[] = []) => {
        records.forEach((kb) => {
          const docCount = kb.documentCount ?? 0;
          documentTotal += docCount;
          if (docCount > 0) {
            activeTotal += 1;
          }
          if (kb.createdBy) {
            creatorNames.add(kb.createdBy);
          }
        });
      };

      addRecords(firstPage.records || []);

      const totalCount = firstPage.total ?? (firstPage.records?.length || 0);
      const totalPages = firstPage.pages || Math.max(1, Math.ceil(totalCount / STATS_PAGE_SIZE));

      for (let page = 2; page <= totalPages; page += 1) {
        const nextPage = await getKnowledgeBasesPage(page, STATS_PAGE_SIZE, normalized || undefined);
        if (statsRequestId.current !== requestId) {
          return;
        }
        addRecords(nextPage.records || []);
      }

      if (statsRequestId.current !== requestId) {
        return;
      }
      setStats({
        totalCount,
        documentCount: documentTotal,
        activeCount: activeTotal,
        creatorCount: creatorNames.size,
      });
    } catch (error) {
      if (statsRequestId.current !== requestId) {
        return;
      }
      console.error(error);
      setStats({
        totalCount: 0,
        documentCount: 0,
        activeCount: 0,
        creatorCount: 0,
      });
    } finally {
      if (statsRequestId.current === requestId) {
        setStatsLoading(false);
      }
    }
  }, [keyword]);

  useEffect(() => {
    loadKnowledgeBases();
  }, [pageNo, keyword]);

  useEffect(() => {
    loadStats(keyword);
  }, [keyword, loadStats]);

  useEffect(() => {
    const trimmed = nameFromQuery.trim();
    if (trimmed !== keyword) {
      setSearchName(trimmed);
      setKeyword(trimmed);
      setPageNo(1);
    }
  }, [nameFromQuery, keyword]);

  useEffect(() => {
    if (renameDialog.open) {
      setRenameValue(renameDialog.kb?.name || "");
    }
  }, [renameDialog]);

  const handleSearch = () => {
    setPageNo(1);
    setKeyword(searchName.trim());
  };

  const handleRefresh = () => {
    setPageNo(1);
    loadKnowledgeBases(1, keyword);
    loadStats(keyword);
  };

  const handleDelete = async () => {
    if (!deleteTarget) {
      return;
    }
    try {
      await deleteKnowledgeBase(deleteTarget.id);
      toast.success("知识库已删除");
      setDeleteTarget(null);
      setPageNo(1);
      await loadKnowledgeBases(1, keyword);
      await loadStats(keyword);
    } catch (error) {
      toast.error(getErrorMessage(error, "删除知识库失败"));
      console.error(error);
    } finally {
      setDeleteTarget(null);
    }
  };

  const handleRename = async () => {
    if (!renameDialog.kb) {
      return;
    }
    const nextName = renameValue.trim();
    if (!nextName) {
      toast.error("请输入知识库名称");
      return;
    }
    if (nextName === renameDialog.kb.name) {
      setRenameDialog({ open: false, kb: null });
      return;
    }
    try {
      await renameKnowledgeBase(renameDialog.kb.id, nextName);
      toast.success("知识库名称已更新");
      setRenameDialog({ open: false, kb: null });
      await loadKnowledgeBases(pageNo, keyword);
    } catch (error) {
      toast.error(getErrorMessage(error, "重命名失败"));
      console.error(error);
    }
  };

  const formatStatValue = (value: number) => (statsLoading ? "--" : value.toLocaleString("zh-CN"));

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">知识库管理</h1>
          <p className="admin-page-subtitle">管理四个顶层知识库的物理库配置和检索元数据。</p>
        </div>
        <div className="admin-page-actions">
          <Input
            value={searchName}
            onChange={(event) => setSearchName(event.target.value)}
            placeholder="搜索知识库名称"
            className="w-[220px]"
          />
          <Button variant="outline" onClick={handleSearch}>
            搜索
          </Button>
          <Button variant="outline" onClick={handleRefresh}>
            <RefreshCw className="mr-2 h-4 w-4" />
            刷新
          </Button>
          <Button className="admin-primary-gradient" onClick={() => setCreateDialogOpen(true)}>
            <Plus className="mr-2 h-4 w-4" />
            新建知识库
          </Button>
        </div>
      </div>

      <div className="admin-stat-grid">
        {[
          { label: "知识库总数", value: stats.totalCount, icon: Database, scope: "全部" },
          { label: "文档总数", value: stats.documentCount, icon: FileBarChart, scope: "全部" },
          { label: "含文档知识库", value: stats.activeCount, icon: FolderOpen, scope: "全部" },
          { label: "创建人数量", value: stats.creatorCount, icon: Layers, scope: "全部" },
        ].map((item) => {
          const Icon = item.icon;
          return (
            <div key={item.label} className="admin-stat-card">
              <div className="flex items-center gap-3">
                <div className="admin-stat-icon">
                  <Icon className="h-5 w-5" />
                </div>
                <div>
                  <div className="admin-stat-label">{item.label}</div>
                  <div className="admin-stat-value">{formatStatValue(item.value)}</div>
                </div>
              </div>
              <span className="admin-stat-scope admin-stat-scope--stamp">{item.scope}</span>
            </div>
          );
        })}
      </div>

      <Card>
        <CardContent className="pt-6">
          {loading ? (
            <div className="py-8 text-center text-muted-foreground">加载中...</div>
          ) : knowledgeBases.length === 0 ? (
            <div className="py-8 text-center text-muted-foreground">
              暂无知识库，点击右上角创建四库中的物理库。
            </div>
          ) : (
            <Table className="min-w-[1320px]">
              <TableHeader>
                <TableRow>
                  <TableHead className="w-[220px]">名称</TableHead>
                  <TableHead className="w-[120px]">库类型</TableHead>
                  <TableHead className="w-[160px]">默认 Profile</TableHead>
                  <TableHead className="w-[220px]">Collection</TableHead>
                  <TableHead className="w-[150px]">Embedding 模型</TableHead>
                  <TableHead className="w-[80px]">文档数</TableHead>
                  <TableHead className="w-[120px]">创建人</TableHead>
                  <TableHead className="w-[160px]">更新时间</TableHead>
                  <TableHead className="w-[200px] text-left">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {knowledgeBases.map((kb) => (
                  <TableRow key={kb.id}>
                    <TableCell className="font-medium">
                      <div className="space-y-1">
                        <button
                          type="button"
                          className="admin-link max-w-[220px] truncate text-left"
                          onClick={() => navigate(`/admin/knowledge/${kb.id}`)}
                        >
                          {kb.name}
                        </button>
                        {kb.description ? (
                          <p className="line-clamp-2 text-xs text-muted-foreground">{kb.description}</p>
                        ) : null}
                      </div>
                    </TableCell>
                    <TableCell>
                      <Badge
                        variant="outline"
                        className={cn("px-3 py-1", getKbTypeBadgeClass(kb.kbType))}
                      >
                        {KB_TYPE_LABELS[kb.kbType || ""] || kb.kbType || "未配置"}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-sm text-muted-foreground">
                      {kb.defaultPipelineProfile || "-"}
                    </TableCell>
                    <TableCell>
                      {kb.collectionName ? (
                        <Badge
                          variant="outline"
                          className={cn("px-3 py-1", getCollectionBadgeClass(kb.collectionName))}
                        >
                          {kb.collectionName}
                        </Badge>
                      ) : (
                        "-"
                      )}
                    </TableCell>
                    <TableCell className="text-sm text-muted-foreground">
                      {kb.embeddingModel || "-"}
                    </TableCell>
                    <TableCell>{kb.documentCount ?? "-"}</TableCell>
                    <TableCell>{kb.createdBy || "-"}</TableCell>
                    <TableCell className="text-muted-foreground">{formatDate(kb.updateTime)}</TableCell>
                    <TableCell>
                      <div className="flex flex-wrap gap-2">
                        <Button variant="outline" size="sm" onClick={() => setDetailTarget(kb)}>
                          查看配置
                        </Button>
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => setRenameDialog({ open: true, kb })}
                        >
                          <Pencil className="mr-1 h-4 w-4" />
                          重命名
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          className="text-destructive hover:text-destructive"
                          onClick={() => setDeleteTarget(kb)}
                        >
                          <Trash2 className="mr-1 h-4 w-4" />
                          删除
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      <AlertDialog open={!!deleteTarget} onOpenChange={() => setDeleteTarget(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>确认删除知识库？</AlertDialogTitle>
            <AlertDialogDescription>
              删除后将不再提供恢复入口，请确认当前物理库和文档数据已处理完成。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>取消</AlertDialogCancel>
            <AlertDialogAction onClick={handleDelete} className="bg-destructive text-destructive-foreground">
              删除
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <Dialog
        open={renameDialog.open}
        onOpenChange={(open) => setRenameDialog({ open, kb: open ? renameDialog.kb : null })}
      >
        <DialogContent className="sm:max-w-[420px]">
          <DialogHeader>
            <DialogTitle>重命名知识库</DialogTitle>
            <DialogDescription>修改知识库名称，不影响 `kbType` 和 `collectionName`。</DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <label className="text-sm font-medium">名称</label>
            <Input value={renameValue} onChange={(event) => setRenameValue(event.target.value)} />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setRenameDialog({ open: false, kb: null })}>
              取消
            </Button>
            <Button onClick={handleRename}>保存</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={!!detailTarget} onOpenChange={(open) => !open && setDetailTarget(null)}>
        <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-[760px]">
          <DialogHeader>
            <DialogTitle>知识库配置详情</DialogTitle>
            <DialogDescription>
              查看当前知识库的类型、默认解析策略和元数据 Schema。
            </DialogDescription>
          </DialogHeader>
          {detailTarget ? (
            <div className="space-y-4">
              <div className="grid gap-4 md:grid-cols-2">
                <div className="rounded-xl border p-4">
                  <div className="text-xs text-muted-foreground">知识库名称</div>
                  <div className="mt-1 text-sm font-medium">{detailTarget.name}</div>
                </div>
                <div className="rounded-xl border p-4">
                  <div className="text-xs text-muted-foreground">库类型</div>
                  <div className="mt-2">
                    <Badge
                      variant="outline"
                      className={cn("px-3 py-1", getKbTypeBadgeClass(detailTarget.kbType))}
                    >
                      {KB_TYPE_LABELS[detailTarget.kbType || ""] || detailTarget.kbType || "未配置"}
                    </Badge>
                  </div>
                </div>
                <div className="rounded-xl border p-4">
                  <div className="text-xs text-muted-foreground">Collection</div>
                  <div className="mt-1 text-sm font-medium">{detailTarget.collectionName || "-"}</div>
                </div>
                <div className="rounded-xl border p-4">
                  <div className="text-xs text-muted-foreground">默认 Profile</div>
                  <div className="mt-1 text-sm font-medium">{detailTarget.defaultPipelineProfile || "-"}</div>
                </div>
              </div>

              <div className="space-y-2">
                <div className="text-sm font-medium">描述</div>
                <Textarea value={detailTarget.description || "-"} readOnly className="min-h-[88px]" />
              </div>

              <div className="grid gap-4 md:grid-cols-2">
                <div className="space-y-2">
                  <div className="text-sm font-medium">路由关键词 JSON</div>
                  <Textarea
                    value={formatJsonPreview(detailTarget.routingKeywordsJson)}
                    readOnly
                    className="min-h-[220px] font-mono text-xs"
                  />
                </div>
                <div className="space-y-2">
                  <div className="text-sm font-medium">Metadata Schema JSON</div>
                  <Textarea
                    value={formatJsonPreview(detailTarget.metadataSchemaJson)}
                    readOnly
                    className="min-h-[220px] font-mono text-xs"
                  />
                </div>
              </div>
            </div>
          ) : null}
        </DialogContent>
      </Dialog>

      {pageData ? (
        <div className="mt-4 flex flex-wrap items-center justify-between gap-2 text-sm text-slate-500">
          <span>共 {pageData.total} 条</span>
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => setPageNo((prev) => Math.max(1, prev - 1))}
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
              onClick={() => setPageNo((prev) => Math.min(pageData.pages || 1, prev + 1))}
              disabled={pageData.current >= pageData.pages}
            >
              下一页
            </Button>
          </div>
        </div>
      ) : null}

      <CreateKnowledgeBaseDialog
        open={createDialogOpen}
        onOpenChange={setCreateDialogOpen}
        onSuccess={() => {
          setPageNo(1);
          loadKnowledgeBases(1, keyword);
          loadStats(keyword);
        }}
      />
    </div>
  );
}
