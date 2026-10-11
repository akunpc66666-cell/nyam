const REDIS_KEY = 'nyam:public:baltop';

function send(res, status, body) {
  res.status(status).setHeader('Content-Type', 'application/json; charset=utf-8');
  res.setHeader('Cache-Control', 'no-store, max-age=0');
  res.end(JSON.stringify(body));
}

async function redisCommand(command) {
  const url = process.env.UPSTASH_REDIS_REST_URL;
  const token = process.env.UPSTASH_REDIS_REST_TOKEN;
  if (!url || !token) throw new Error('Redis environment variables are missing');
  const response = await fetch(url.replace(/\/$/, ''), {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(command)
  });
  if (!response.ok) throw new Error(`Redis HTTP ${response.status}`);
  const result = await response.json();
  if (result.error) throw new Error('Redis command failed');
  return result.result;
}

module.exports = async function handler(req, res) {
  if (req.method === 'GET') {
    try {
      const value = await redisCommand(['GET', REDIS_KEY]);
      if (!value) return send(res, 200, { players: [], updatedAt: null, message: 'Belum ada data dari server Minecraft.' });
      let payload;
      try { payload = JSON.parse(value); } catch { return send(res, 502, { error: 'Stored leaderboard data is invalid' }); }
      return send(res, 200, payload);
    } catch (error) {
      return send(res, 503, { error: 'Leaderboard service is not configured or temporarily unavailable' });
    }
  }

  if (req.method !== 'POST') {
    res.setHeader('Allow', 'GET, POST');
    return send(res, 405, { error: 'Method not allowed' });
  }

  const secret = process.env.NYAM_INGEST_SECRET;
  const auth = req.headers.authorization || '';
  if (!secret || auth !== `Bearer ${secret}`) return send(res, 401, { error: 'Unauthorized' });

  try {
    let body = req.body;
    if (typeof body === 'string') body = JSON.parse(body);
    if (!body || !Array.isArray(body.players)) return send(res, 400, { error: 'Expected a players array' });
    const players = body.players
      .filter(p => p && typeof p.name === 'string' && p.name.trim() && p.name.length <= 64 && Number.isFinite(Number(p.balance)) && Number(p.balance) >= 0)
      .map(p => ({ name: p.name.trim(), balance: Number(p.balance) }))
      .sort((a, b) => b.balance - a.balance)
      .slice(0, 10);
    const payload = { players, updatedAt: new Date().toISOString() };
    await redisCommand(['SET', REDIS_KEY, JSON.stringify(payload)]);
    return send(res, 200, { ok: true, count: players.length, updatedAt: payload.updatedAt });
  } catch (error) {
    return send(res, 500, { error: 'Could not save leaderboard data' });
  }
};
