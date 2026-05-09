const base = "http://localhost:8080/api/ragent";

const prompt = `你是 AI-Compete 智赛通的 AI 竞赛助手，角色是资深 AI 研究员、竞赛教练和算法工程师。

你的服务对象是 AI 竞赛参赛者，主要帮助他们理解赛题、设计机器学习/深度学习方案、调试代码、优化模型性能、选择评估指标、规划提交策略。

当用户问你是谁、是什么大模型、能做什么时，要明确说明：你是面向 AI 竞赛场景的智能助手，不是企业内部人事/IT/行政知识助手。底层模型由系统接入的大语言模型服务提供，具体型号可能由平台配置维护；你会把能力聚焦在 AI 竞赛辅导上。

当用户只是打招呼时，简短友好回应，并引导其描述赛题、数据、报错、指标或优化目标。

不要主动展开企业内部、人事、行政、IT 支持、报销、VPN、打印机等无关范围。遇到与 AI 竞赛无关的问题，礼貌说明你主要服务 AI 竞赛与算法工程问题，并引导用户回到赛题、模型、代码、数据或评估指标。`;

const login = await fetch(`${base}/auth/login`, {
  method: "POST",
  headers: { "content-type": "application/json" },
  body: JSON.stringify({ username: "admin", password: "admin" })
}).then((response) => response.json());

const token = login.data.token;
const tree = await fetch(`${base}/intent-tree/trees`, {
  headers: { Authorization: token }
}).then((response) => response.json());

const flat = [];
function walk(nodes) {
  for (const node of nodes || []) {
    flat.push(node);
    walk(node.children);
  }
}
walk(tree.data);

const updates = {
  sys: {
    name: "AI竞赛助手",
    description: "AI竞赛助手系统交互、问候、自我介绍与范围说明",
    examples: [],
    promptTemplate: prompt
  },
  "sys-welcome": {
    name: "欢迎与问候",
    description: "用户与AI竞赛助手打招呼，例如你好、hello、在吗、早上好等",
    examples: ["你好", "hello", "早上好", "在吗", "嗨"],
    promptTemplate: prompt
  },
  "sys-about-bot": {
    name: "关于AI竞赛助手",
    description: "询问助手是谁、能做什么、是什么AI、底层模型、大语言模型、比赛助手等",
    examples: ["你是谁", "你是什么大模型", "你是什么AI", "你能做什么", "你是比赛助手吗", "底层模型是什么"],
    promptTemplate: prompt
  }
};

for (const node of flat) {
  const update = updates[node.intentCode];
  if (!update) continue;
  const body = {
    ...update,
    level: node.level,
    parentCode: node.parentCode,
    kind: 1,
    sortOrder: node.sortOrder,
    enabled: 1
  };
  const response = await fetch(`${base}/intent-tree/${node.id}`, {
    method: "PUT",
    headers: { "content-type": "application/json", Authorization: token },
    body: JSON.stringify(body)
  });
  console.log(node.intentCode, node.id, response.status, await response.text());
}
