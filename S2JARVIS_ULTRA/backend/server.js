import express from "express";
import dotenv from "dotenv";

dotenv.config();
const app = express();
app.use(express.json({ limit: "32kb" }));

app.get("/health", (_req, res) => res.json({ ok: true, service: "S2 JARVIS AI" }));

app.post("/chat", async (req, res) => {
  try {
    const message = String(req.body?.message || "").trim();
    const history = Array.isArray(req.body?.history) ? req.body.history.slice(-10) : [];
    if (!message) return res.status(400).json({ error: "Message is required" });
    if (!process.env.GEMINI_API_KEY) return res.status(503).json({ error: "AI server key is not configured" });

    const contents = [];
    for (const item of history) {
      const role = item?.role === "assistant" ? "model" : "user";
      const content = String(item?.content || "").slice(0, 4000);
      if (content) contents.push({ role, parts: [{ text: content }] });
    }
    contents.push({ role: "user", parts: [{ text: message.slice(0, 8000) }] });

    const model = process.env.GEMINI_MODEL || "gemini-2.5-flash";
    const endpoint = `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:generateContent`;
    const response = await fetch(endpoint, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "x-goog-api-key": process.env.GEMINI_API_KEY
      },
      body: JSON.stringify({
        systemInstruction: {
          parts: [{ text: "You are S2 JARVIS, a capable, calm, friendly personal voice assistant inspired by modern phone assistants. Speak naturally and conversationally. Match the user's language and script when practical: Hindi, Punjabi, or English. Keep spoken answers concise by default, but explain more when asked. Understand casual Hinglish and follow-up context. Give step-by-step help for tasks. Never claim you performed an action on the phone, sent a message, made a call, changed a setting, or accessed private data unless the Android app explicitly confirms it. For consequential actions, ask the user to confirm. Be accurate and say clearly when you are unsure." }]
        },
        contents,
        generationConfig: { temperature: 0.7, maxOutputTokens: 700 }
      })
    });
    const data = await response.json();
    if (!response.ok) {
      return res.status(response.status).json({
        error: data?.error?.message || "AI provider request failed",
        providerStatus: response.status
      });
    }
    const reply = data?.candidates?.[0]?.content?.parts?.map(p => p.text || "").join("").trim();
    if (!reply) return res.status(502).json({ error: "AI provider returned an empty response" });
    res.json({ reply });
  } catch (err) {
    res.status(500).json({ error: "Backend error", detail: String(err?.message || err) });
  }
});

const port = Number(process.env.PORT || 3000);
app.listen(port, "0.0.0.0", () => console.log(`S2 JARVIS backend listening on ${port}`));
