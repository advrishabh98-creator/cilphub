const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_streamer_1";
const BOB_UID = "bob_streamer_2";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validStreamCreatePayload(uid, overrides = {}) {
  const past = new Date(Date.now() - 5000);
  return {
    userId: uid,
    title: "Ranked Grind to Radiant - Insane Overtime",
    channelName: "shroud",
    sourceType: "VIDEO_FILE",
    sourceRef: "ranked_vod_01.mp4",
    durationSeconds: 1840,
    clipCount: 0,
    peakHypeScore: 0,
    summary: "Analyzing stream keyframes...",
    status: "ANALYZING",
    createdAt: past,
    updatedAt: past,
    ...overrides,
  };
}

function validClipCreatePayload(uid, streamId, overrides = {}) {
  const past = new Date(Date.now() - 5000);
  return {
    userId: uid,
    streamId,
    streamTitle: "Ranked Grind to Radiant - Insane Overtime",
    channelName: "shroud",
    title: "1v4 Sheriff Clutch on Haven C Site",
    hookCaption: "WAIT FOR THE LAST SHOT...",
    engagementReason: "Rapid multi-kill climax with sudden audio/visual spikes.",
    category: "CLUTCH_PLAY",
    startSec: 412,
    endSec: 448,
    hypeScore: 96,
    starred: false,
    exported: false,
    createdAt: past,
    updatedAt: past,
    ...overrides,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read or write streams or clips", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/streams`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/clips`).get());
  await assertFails(
    unauthDb
      .collection(`users/${ALICE_UID}/streams`)
      .doc("stream_1")
      .set(validStreamCreatePayload(ALICE_UID))
  );
  await assertFails(
    unauthDb
      .collection(`users/${ALICE_UID}/clips`)
      .doc("clip_1")
      .set(validClipCreatePayload(ALICE_UID, "stream_1"))
  );
});

test("Owner: stream create, transition ANALYZING -> READY, and clip creation succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const streamRef = aliceDb.collection(`users/${ALICE_UID}/streams`).doc("stream_1");

  await assertSucceeds(streamRef.set(validStreamCreatePayload(ALICE_UID)));

  // Transition ANALYZING -> READY
  await assertSucceeds(
    streamRef.update({
      status: "READY",
      durationSeconds: 1840,
      clipCount: 1,
      peakHypeScore: 96,
      summary: "3 high-intensity clutch and reaction highlights found.",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Create clip referencing existing stream_1 (without optional localVideoUri)
  const clipRef = aliceDb.collection(`users/${ALICE_UID}/clips`).doc("clip_1");
  await assertSucceeds(clipRef.set(validClipCreatePayload(ALICE_UID, "stream_1")));

  // Create clip WITH optional localVideoUri
  const clip2Ref = aliceDb.collection(`users/${ALICE_UID}/clips`).doc("clip_2");
  await assertSucceeds(
    clip2Ref.set(
      validClipCreatePayload(ALICE_UID, "stream_1", {
        localVideoUri: "content://media/picker/0/com.android.providers.media.photopicker/media/42",
      })
    )
  );

  // List queries match Kotlin Repository paths
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/streams`).get());
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/clips`).get());
  await assertSucceeds(
    aliceDb
      .collection(`users/${ALICE_UID}/clips`)
      .where("streamId", "==", "stream_1")
      .get()
  );
});

test("Owner: clip partial updates (star, export, trim/edit) succeed and preserve immutables", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb
      .collection(`users/${ALICE_UID}/streams`)
      .doc("stream_1")
      .set(validStreamCreatePayload(ALICE_UID))
  );
  const clipRef = aliceDb.collection(`users/${ALICE_UID}/clips`).doc("clip_1");
  await assertSucceeds(clipRef.set(validClipCreatePayload(ALICE_UID, "stream_1")));

  // Action: toggle starred
  await assertSucceeds(
    clipRef.update({
      starred: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Action: mark exported
  await assertSucceeds(
    clipRef.update({
      exported: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Action: edit metadata and trim bounds
  await assertSucceeds(
    clipRef.update({
      title: "Updated Clutch Title",
      hookCaption: "UNREAL FLICK",
      category: "HIGHLIGHT",
      startSec: 410,
      endSec: 455,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject invalid trim bounds (endSec <= startSec)
  await assertFails(
    clipRef.update({
      title: "Bad Trim",
      hookCaption: "Nope",
      category: "HIGHLIGHT",
      startSec: 500,
      endSec: 450,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject immutable streamId / createdAt change
  await assertFails(
    clipRef.update({
      streamId: "stream_other",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    clipRef.update({
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Cross-user isolation: Bob cannot read, list, create, update, or delete Alice's data", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db
      .collection(`users/${ALICE_UID}/streams`)
      .doc("stream_1")
      .set(validStreamCreatePayload(ALICE_UID));
    await db
      .collection(`users/${ALICE_UID}/clips`)
      .doc("clip_1")
      .set(validClipCreatePayload(ALICE_UID, "stream_1"));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection(`users/${ALICE_UID}/streams`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/streams`).doc("stream_1").get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/clips`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/clips`).doc("clip_1").get());
  await assertFails(
    bobDb
      .collection(`users/${ALICE_UID}/clips`)
      .doc("clip_1")
      .update({ starred: true, updatedAt: new Date(Date.now() - 1000) })
  );
  await assertFails(bobDb.collection(`users/${ALICE_UID}/clips`).doc("clip_1").delete());
});

test("Security hardening: rejects ghost fields, @DocumentId id field, orphaned clips, future timestamps, and terminal status tampering", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb
      .collection(`users/${ALICE_UID}/streams`)
      .doc("stream_1")
      .set(validStreamCreatePayload(ALICE_UID, { status: "READY" }))
  );

  // Terminal status cannot be reverted back to ANALYZING
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/streams`).doc("stream_1").update({
      status: "ANALYZING",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject ghost field
  await assertFails(
    aliceDb
      .collection(`users/${ALICE_UID}/clips`)
      .doc("clip_ghost")
      .set({
        ...validClipCreatePayload(ALICE_UID, "stream_1"),
        isAdminVerified: true,
      })
  );

  // Reject explicit id field (@DocumentId guard)
  await assertFails(
    aliceDb
      .collection(`users/${ALICE_UID}/clips`)
      .doc("clip_with_id")
      .set({
        ...validClipCreatePayload(ALICE_UID, "stream_1"),
        id: "clip_with_id",
      })
  );

  // Reject orphaned clip (non-existent streamId)
  await assertFails(
    aliceDb
      .collection(`users/${ALICE_UID}/clips`)
      .doc("clip_orphan")
      .set(validClipCreatePayload(ALICE_UID, "nonexistent_stream"))
  );

  // Reject future timestamp
  await assertFails(
    aliceDb
      .collection(`users/${ALICE_UID}/clips`)
      .doc("clip_future")
      .set(
        validClipCreatePayload(ALICE_UID, "stream_1", {
          createdAt: new Date(Date.now() + 60000),
        })
      )
  );
});
