import crypto from 'node:crypto'
import mineflayer from 'mineflayer'

const port = Number(process.env.BRUSHCUI_E2E_PORT ?? 25565)
const timeoutMs = 20_000
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))

// Same as Java's UUID.nameUUIDFromBytes("BrushCUI:brush").
const REGION_ID = (() => {
  const hash = crypto.createHash('md5').update('BrushCUI:brush').digest()
  hash[6] = (hash[6] & 0x0f) | 0x30
  hash[8] = (hash[8] & 0x3f) | 0x80
  const hex = hash.toString('hex')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
})()

function assert(condition, message) {
  if (!condition) throw new Error(message)
}

function assertEqual(actual, expected, message) {
  const a = JSON.stringify(actual)
  const e = JSON.stringify(expected)
  if (a !== e) throw new Error(`${message}\n  expected: ${e}\n  actual:   ${a}`)
}

function createBot(username) {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port, username, version: '1.21.11', auth: 'offline' })
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error(`${username} did not spawn`)), timeoutMs)
    bot.once('spawn', () => { clearTimeout(timeout); resolve(bot) })
    bot.once('kicked', reason => reject(new Error(`${username} kicked: ${JSON.stringify(reason)}`)))
    bot.once('error', reject)
  })
}

/** Records preview (multi-region) CUI messages received by a bot with a CUI "mod". */
class CuiRecorder {
  constructor(bot) {
    this.messages = []
    bot._client.on('custom_payload', packet => {
      if (packet.channel !== 'worldedit:cui') return
      const text = packet.data.toString('utf8')
      if (text.startsWith('+')) this.messages.push({ at: Date.now(), text })
    })
  }

  mark() {
    return this.messages.length
  }

  /** Batches (each starting with +s) received since a mark. */
  batches(since = 0) {
    const result = []
    for (const { text } of this.messages.slice(since)) {
      if (text.startsWith('+s|')) result.push([])
      if (result.length) result[result.length - 1].push(text)
    }
    return result
  }

  async waitForBatch(since, predicate, description) {
    const deadline = Date.now() + timeoutMs
    while (Date.now() < deadline) {
      // A batch is complete once its +col (and for cuboids +grid) arrived; wait one more tick.
      const found = this.batches(since).find(batch => predicate(batch) && batch.some(m => m.startsWith('+col|')))
      if (found) {
        await sleep(100)
        return this.batches(since).find(batch => batch[0] === found[0] && predicate(batch))
      }
      await sleep(25)
    }
    throw new Error(`Timed out waiting for ${description}; got ${JSON.stringify(this.batches(since))}`)
  }
}

const MOVE_PACKETS = new Set(['entity_teleport', 'sync_entity_position', 'teleport_entity', 'rel_entity_move'])
const COUNTED_PACKETS = new Set(['spawn_entity', 'entity_destroy', 'entity_metadata', 'set_passengers',
  'bundle_delimiter', 'custom_payload', ...MOVE_PACKETS])

/**
 * Counts the clientbound packets that render the preview. Spawns and movement only count for
 * Text Display entities (lines and their root anchors), so a preview that is moved by teleporting
 * its entities is measured the same way as one that is respawned, and mobs are ignored.
 */
class PacketCounter {
  constructor(bot) {
    this.counts = new Map()
    this.spawns = 0
    this.textDisplaySpawns = 0
    this.moves = 0
    this.renderedIds = new Set()
    const textDisplayId = bot.registry.entitiesByName.text_display.id
    bot._client.on('packet', (data, meta) => {
      if (meta.name === 'spawn_entity' && data.type === textDisplayId) {
        this.renderedIds.add(data.entityId)
        this.spawns++
        this.textDisplaySpawns++
      }
      if (MOVE_PACKETS.has(meta.name)) {
        if (!this.renderedIds.has(data.entityId)) return
        this.moves++
      }
      if (COUNTED_PACKETS.has(meta.name)) this.counts.set(meta.name, (this.counts.get(meta.name) ?? 0) + 1)
    })
  }

  snapshot() {
    return { counts: new Map(this.counts), spawns: this.spawns, textDisplaySpawns: this.textDisplaySpawns, moves: this.moves }
  }

  since(snapshot) {
    const diff = {}
    let total = 0
    for (const [name, count] of this.counts) {
      const delta = count - (snapshot.counts.get(name) ?? 0)
      if (delta > 0) {
        diff[name] = delta
        total += delta
      }
    }
    const spawns = this.spawns - snapshot.spawns
    const moves = this.moves - snapshot.moves
    return {
      ...diff,
      entityAndCuiPackets: total,
      textDisplaySpawns: this.textDisplaySpawns - snapshot.textDisplaySpawns,
      previewSpawns: spawns,
      previewMoves: moves,
      previewUpdates: spawns + moves
    }
  }
}

async function command(bot, text) {
  bot.chat(text)
  await sleep(350) // stay well below the chat spam limit
}

function liveTextDisplays(bot) {
  return Object.values(bot.entities).filter(entity => entity.name === 'text_display').length
}

const results = {}
// Set when the WorldEditDisplay under test supports the +wed|outline hint (2.9.0+).
const expectOutline = process.env.BRUSHCUI_E2E_EXPECT_OUTLINE === '1'
const LINE = '#FFAA00FF'
const CENTER = '#FFFFFFFF'
const CLEAR = '#00000000'

function sphere(center, radius) {
  return [
    `+s|ellipsoid|${REGION_ID}`,
    `+e|0|${center.join('|')}`,
    `+e|1|${radius}|${radius}|${radius}`,
    `+col|${LINE}|${CENTER}|${CENTER}|${CLEAR}`,
    '+wed|outline'
  ]
}

function cylinder(center, radius, minY, maxY) {
  return [
    `+s|cylinder|${REGION_ID}`,
    `+cyl|${center.join('|')}|${radius}|${radius}`,
    `+mm|${minY}|${maxY}`,
    `+col|${LINE}|#FFAA0060|${CENTER}|${CLEAR}`,
    '+wed|outline'
  ]
}

function cuboid(min, max) {
  const volume = (max[0] - min[0] + 1) * (max[1] - min[1] + 1) * (max[2] - min[2] + 1)
  return [
    `+s|cuboid|${REGION_ID}`,
    `+p|0|${min.join('|')}|${volume}`,
    `+p|1|${max.join('|')}|${volume}`,
    `+col|${LINE}|${CLEAR}|${CLEAR}|${CLEAR}`,
    '+grid|0'
  ]
}

/** Creative mode, a stick in slot 0, an empty slot 1, standing at (x, -60, z) looking straight down. */
async function prepare(bot, x, z) {
  await command(bot, `/gamemode creative ${bot.username}`)
  await command(bot, `/clear ${bot.username}`)
  await command(bot, `/give ${bot.username} minecraft:stick`)
  bot.setQuickBarSlot(0)
  await command(bot, `/tp ${bot.username} ${x + 0.5} -60 ${z + 0.5} 0 90`)
  await bot.look(0, -Math.PI / 2, true)
  await sleep(300)
}

async function waitForExactBatch(cui, mark, expected, description) {
  return cui.waitForBatch(mark, b => JSON.stringify(b) === JSON.stringify(expected), description)
}

async function waitForClear(cui, mark, description) {
  const deadline = Date.now() + timeoutMs
  while (!cui.messages.slice(mark).some(m => m.text === `+s|clear|${REGION_ID}`)) {
    assert(Date.now() < deadline, `no clear message ${description}`)
    await sleep(25)
  }
  assert(!cui.messages.slice(mark).some(m => m.text === '+s|clear'), 'must never clear all multi regions')
}

async function nativeCuiScenario() {
  const bot = await createBot('BrushNative')
  const cui = new CuiRecorder(bot)
  // Behave like WorldEditCUI: register the channel and send the protocol handshake.
  bot._client.write('custom_payload', { channel: 'minecraft:register', data: Buffer.from('worldedit:cui', 'utf8') })
  bot._client.write('custom_payload', { channel: 'worldedit:cui', data: Buffer.from('v|4', 'utf8') })
  await sleep(1500)
  await prepare(bot, 0, 0)

  // Looking straight down from y -60 targets the grass block at y -61.
  let mark = cui.mark()
  await command(bot, '//brush sphere stone 3')
  // FAWE runs commands asynchronously and sets the size after binding the brush, so a first
  // update with the default size may come before the final one.
  let batch = await waitForExactBatch(cui, mark, sphere([0, -61, 0], 3), 'sphere brush preview')

  mark = cui.mark()
  await command(bot, '//brush cylinder stone 2 3')
  await waitForExactBatch(cui, mark, cylinder([0, -61, 0], 2, -61, -59), 'cylinder brush growing up from the target')

  mark = cui.mark()
  await command(bot, '//brush smooth 2')
  await waitForExactBatch(cui, mark, cuboid([-2, -63, -2], [2, -49, 2]), 'smooth brush height map region')

  mark = cui.mark()
  await command(bot, '//brush sphere stone 3')
  await cui.waitForBatch(mark, b => b[0].startsWith('+s|ellipsoid'), 'sphere brush again')

  // Following the crosshair: the target moves with the player.
  mark = cui.mark()
  await command(bot, '/tp BrushNative 6.5 -60 0.5')
  await bot.look(0, -Math.PI / 2, true)
  batch = await cui.waitForBatch(mark, b => b[1] === '+e|0|6|-61|0', 'preview after teleport')
  assertEqual(batch, sphere([6, -61, 0], 3), 'preview follows the target')

  // Looking at the sky: WorldEdit finds no block and shows nothing; FAWE's trace returns the
  // air block at the end of the range (240 blocks up), which is where its brush would apply.
  mark = cui.mark()
  await bot.look(0, Math.PI / 2, true)
  if (process.env.BRUSHCUI_E2E_EDITOR === 'fawe') {
    await cui.waitForBatch(mark, b => b[1].startsWith('+e|0|6|') && Number(b[1].split('|')[3]) > 150,
      'preview at the end of the range')
  } else {
    await waitForClear(cui, mark, 'when no block is targeted')
  }
  mark = cui.mark()
  await bot.look(0, -Math.PI / 2, true)
  await cui.waitForBatch(mark, b => b[0].startsWith('+s|ellipsoid'), 'preview after looking down again')

  mark = cui.mark()
  bot.setQuickBarSlot(1)
  await waitForClear(cui, mark, 'after switching to an item without a brush')
  mark = cui.mark()
  bot.setQuickBarSlot(0)
  await cui.waitForBatch(mark, b => b[0].startsWith('+s|ellipsoid'), 'preview after switching back')

  mark = cui.mark()
  await command(bot, '/brushcui off')
  await waitForClear(cui, mark, 'after /brushcui off')
  mark = cui.mark()
  await command(bot, '/brushcui on')
  await cui.waitForBatch(mark, b => b[0].startsWith('+s|ellipsoid'), 'preview after /brushcui on')

  // A repeated handshake (WorldEditCUI does this on world change) drops client regions.
  await sleep(500)
  mark = cui.mark()
  bot._client.write('custom_payload', { channel: 'worldedit:cui', data: Buffer.from('v|4', 'utf8') })
  await cui.waitForBatch(mark, b => b[0].startsWith('+s|ellipsoid'), 'resend after handshake')

  // Standing still sends nothing.
  await sleep(500)
  mark = cui.mark()
  await sleep(1500)
  assertEqual(cui.batches(mark).length, 0, 'a still target is not re-sent')

  mark = cui.mark()
  await command(bot, '/tool none')
  await waitForClear(cui, mark, 'after unbinding the brush')
  results.native = 'ok'
  bot.quit()
}

async function worldEditDisplayScenario() {
  const bot = await createBot('BrushWed')
  const packets = new PacketCounter(bot)
  const cui = new CuiRecorder(bot)
  await sleep(2500) // WorldEditDisplay emulates the CUI handshake one second after join
  await prepare(bot, 0, 40)

  await command(bot, '//brush sphere stone 5')
  await sleep(2000)
  const sphereEntities = liveTextDisplays(bot)
  assert(sphereEntities > 0, 'WorldEditDisplay did not render the brush preview')
  assertEqual(cui.messages.length, 0, 'WorldEditDisplay must consume the CUI messages instead of forwarding them')

  let snapshot = packets.snapshot()
  await command(bot, '/tp BrushWed 3.5 -60 40.5')
  await bot.look(0, -Math.PI / 2, true)
  await sleep(1500)
  const moveUpdate = packets.since(snapshot)
  assert(moveUpdate.previewMoves > 0, `the preview did not move: ${JSON.stringify(moveUpdate)}`)
  assertEqual(moveUpdate.textDisplaySpawns, 0, 'a moved preview is teleported, not respawned')
  assertEqual(liveTextDisplays(bot), sphereEntities, 'the moved preview has the same entity count')

  await command(bot, '//brush cylinder stone 5 4')
  await sleep(2000)
  const cylinderEntities = liveTextDisplays(bot)

  results.worldEditDisplay = { expectOutline, sphereEntities, cylinderEntities, moveUpdate }
  if (expectOutline) {
    // Three great circles plus the centre marker (~100 lines of 3 entities each). Without the
    // hint WorldEditDisplay 2.8.1 draws the full radius 5 ellipsoid with ~1400 entities.
    assert(sphereEntities <= 400, `outline sphere used ${sphereEntities} entities`)
    assert(cylinderEntities <= 300, `outline cylinder used ${cylinderEntities} entities`)
  }

  bot.setQuickBarSlot(1)
  await sleep(1500)
  assertEqual(liveTextDisplays(bot), 0, 'the preview is removed when the brush is put away')
  bot.quit()
}

try {
  await nativeCuiScenario()
  await worldEditDisplayScenario()
  console.log('BRUSHCUI_E2E_RESULTS ' + JSON.stringify(results, null, 2))
  console.log('BrushCUI e2e passed')
  process.exit(0)
} catch (error) {
  console.error(error)
  console.error('partial results: ' + JSON.stringify(results, null, 2))
  process.exit(1)
}
