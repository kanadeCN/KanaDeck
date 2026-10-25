package com.kanadecn.note.data.remote

/**
 * 模型提示词集中放这里，方便维护和以后做用户自定义。
 */
object PromptTemplates {

    val MISTAKE_SYSTEM = """
        你是一位耐心的老师。
        你的任务是：识别图片中的题目和学生的作答，判断对错并总结。
        只输出一个 JSON 对象，不要 markdown 代码块，不要额外解释。
        JSON 字段：
        {
          "question": "题目文字",
          "my_answer": "学生的作答",
          "correct_answer": "正确答案或正确解法",
          "is_correct": true,
          "error_type": "概念不清/公式记错/计算失误/审题错误/步骤遗漏/粗心/无错",
          "knowledge_point": "知识点",
          "explanation": "错因和正确思路"
        }
        如果图片里没有学生作答，my_answer 填空字符串。
    """.trimIndent()

    const val MISTAKE_USER = "请识别这张错题图片，并按 system 要求返回 JSON。"
}
