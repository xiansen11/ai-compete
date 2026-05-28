import { useEffect, useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import { toast } from "sonner";

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
import { Textarea } from "@/components/ui/textarea";
import { Button } from "@/components/ui/button";

import { createKnowledgeBase } from "@/services/knowledgeService";
import { getSystemSettings, type ModelCandidate } from "@/services/settingsService";
import { getErrorMessage } from "@/utils/error";

const KB_TYPE_OPTIONS = [
  { value: "GUIDE", label: "办事指南库" },
  { value: "RULE", label: "赛场规则库" },
  { value: "PITFALL", label: "技术踩坑库" },
  { value: "EXEMPLAR", label: "满分作业库" },
] as const;

const formSchema = z.object({
  name: z.string().min(1, "请输入知识库名称").max(50, "名称不能超过 50 个字符"),
  embeddingModel: z.string().min(1, "请选择 Embedding 模型"),
  kbType: z.enum(["GUIDE", "RULE", "PITFALL", "EXEMPLAR"]),
  collectionName: z
    .string()
    .min(1, "请输入 Collection 名称")
    .max(50, "Collection 名称不能超过 50 个字符")
    .regex(/^[a-z0-9_]+$/, "只能包含小写字母、数字和下划线"),
  description: z.string().optional(),
  routingKeywordsJson: z.string().optional(),
  metadataSchemaJson: z.string().optional(),
  defaultPipelineProfile: z.string().optional(),
});

type FormValues = z.infer<typeof formSchema>;

interface CreateKnowledgeBaseDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
}

export function CreateKnowledgeBaseDialog({
  open,
  onOpenChange,
  onSuccess,
}: CreateKnowledgeBaseDialogProps) {
  const [loading, setLoading] = useState(false);
  const [modelLoading, setModelLoading] = useState(false);
  const [embeddingModels, setEmbeddingModels] = useState<ModelCandidate[]>([]);

  const form = useForm<FormValues>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      name: "",
      embeddingModel: "",
      kbType: "GUIDE",
      collectionName: "",
      description: "",
      routingKeywordsJson: "",
      metadataSchemaJson: "",
      defaultPipelineProfile: "",
    },
  });

  useEffect(() => {
    if (!open) {
      return;
    }
    let active = true;
    setModelLoading(true);
    getSystemSettings()
      .then((settings) => {
        if (!active) {
          return;
        }
        const candidates = settings.ai?.embedding?.candidates || [];
        setEmbeddingModels(candidates.filter((item) => item.enabled !== false));
      })
      .catch(() => {
        if (active) {
          setEmbeddingModels([]);
        }
      })
      .finally(() => {
        if (active) {
          setModelLoading(false);
        }
      });
    return () => {
      active = false;
    };
  }, [open]);

  const selectOptions = useMemo(() => {
    const uniqueMap = new Map<string, ModelCandidate>();
    embeddingModels.forEach((item) => {
      if (item.id) {
        uniqueMap.set(item.id, item);
      }
    });
    return Array.from(uniqueMap.values());
  }, [embeddingModels]);

  const handleReset = () => {
    form.reset({
      name: "",
      embeddingModel: "",
      kbType: "GUIDE",
      collectionName: "",
      description: "",
      routingKeywordsJson: "",
      metadataSchemaJson: "",
      defaultPipelineProfile: "",
    });
  };

  const handleDialogOpenChange = (nextOpen: boolean) => {
    if (!nextOpen) {
      handleReset();
    }
    onOpenChange(nextOpen);
  };

  const onSubmit = async (values: FormValues) => {
    try {
      setLoading(true);
      await createKnowledgeBase(values);
      toast.success("知识库已创建");
      handleDialogOpenChange(false);
      onSuccess();
    } catch (error) {
      toast.error(getErrorMessage(error, "创建知识库失败"));
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={handleDialogOpenChange}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-[680px]">
        <DialogHeader>
          <DialogTitle>创建知识库</DialogTitle>
          <DialogDescription>
            创建四库架构中的物理知识库，并配置默认路由与解析信息。
          </DialogDescription>
        </DialogHeader>

        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            <FormField
              control={form.control}
              name="name"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>知识库名称</FormLabel>
                  <FormControl>
                    <Input placeholder="例如：办事指南库" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <div className="grid gap-4 md:grid-cols-2">
              <FormField
                control={form.control}
                name="kbType"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>知识库类型</FormLabel>
                    <Select value={field.value} onValueChange={field.onChange}>
                      <FormControl>
                        <SelectTrigger>
                          <SelectValue placeholder="选择知识库类型" />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        {KB_TYPE_OPTIONS.map((item) => (
                          <SelectItem key={item.value} value={item.value}>
                            {item.label}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    <FormDescription>
                      按业务语义定义知识库，不由文件扩展名决定归属。
                    </FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name="embeddingModel"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Embedding 模型</FormLabel>
                    <Select value={field.value} onValueChange={field.onChange}>
                      <FormControl>
                        <SelectTrigger>
                          <SelectValue placeholder="选择 Embedding 模型" />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        {modelLoading ? (
                          <SelectItem value="loading" disabled>
                            加载中...
                          </SelectItem>
                        ) : selectOptions.length === 0 ? (
                          <SelectItem value="empty" disabled>
                            暂无可用模型
                          </SelectItem>
                        ) : (
                          selectOptions.map((item) => {
                            const label = item.provider && item.model
                              ? `${item.provider} / ${item.model}`
                              : item.model || item.id;
                            return (
                              <SelectItem key={item.id} value={item.id}>
                                {label}
                              </SelectItem>
                            );
                          })
                        )}
                      </SelectContent>
                    </Select>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>

            <FormField
              control={form.control}
              name="collectionName"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Collection 名称</FormLabel>
                  <FormControl>
                    <Input placeholder="例如：competition_guide" {...field} />
                  </FormControl>
                  <FormDescription>
                    仅用于物理存储标识，不代表业务分类。
                  </FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="description"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>描述</FormLabel>
                  <FormControl>
                    <Textarea
                      placeholder="说明这个知识库承载的业务范围、适用问题和典型文档。"
                      className="min-h-[96px]"
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="defaultPipelineProfile"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>默认解析 Profile</FormLabel>
                  <FormControl>
                    <Input
                      placeholder="例如：guide_structure / rule_layout / pitfall_code / exemplar_summary"
                      {...field}
                    />
                  </FormControl>
                  <FormDescription>
                    供上传后默认解析与切片策略选择使用。
                  </FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />

            <div className="grid gap-4 md:grid-cols-2">
              <FormField
                control={form.control}
                name="routingKeywordsJson"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>路由关键词 JSON</FormLabel>
                    <FormControl>
                      <Textarea
                        placeholder='{"keywords":["报名","FAQ","赛区政策"]}'
                        className="min-h-[140px] font-mono text-xs"
                        {...field}
                      />
                    </FormControl>
                    <FormDescription>
                      用于后台内容分类器的提示词和关键词配置。
                    </FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name="metadataSchemaJson"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Metadata Schema JSON</FormLabel>
                    <FormControl>
                      <Textarea
                        placeholder='{"fields":["province","year","stage"]}'
                        className="min-h-[140px] font-mono text-xs"
                        {...field}
                      />
                    </FormControl>
                    <FormDescription>
                      描述该库支持的元数据字段，用于检索硬过滤。
                    </FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => handleDialogOpenChange(false)} disabled={loading}>
                取消
              </Button>
              <Button type="submit" disabled={loading}>
                {loading ? "创建中..." : "创建知识库"}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
