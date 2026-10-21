'use strict';
const { onDocumentWritten } = require('firebase-functions/v2/firestore');
const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const { getAuth } = require('firebase-admin/auth');
const { getMessaging } = require('firebase-admin/messaging');
const { createHash } = require('node:crypto');
const { route } = require('./routing');
initializeApp();
const db = getFirestore();
async function deliver(event, kind) {
  const before = event.data.before.exists ? event.data.before.data() : null;
  const after = event.data.after.exists ? event.data.after.data() : null;
  const target = route(kind, before, after);
  if (!target) return;
  const recipients = await db.collection('pushRecipients')
    .where(target.audience === 'admin' ? 'admin' : 'employeeId', '==', target.audience === 'admin' ? true : target.employeeId).get();
  for (const recipient of recipients.docs) {
    // Recheck authoritative Auth claims, so stale mappings cannot grant delivery.
    let account;
    try { account = await getAuth().getUser(recipient.id); }
    catch (error) { if (error.code === 'auth/user-not-found') continue; throw error; }
    const claims = account.customClaims || {};
    if (account.disabled || (target.audience === 'admin' ? claims.admin !== true : claims.employeeId !== target.employeeId)) continue;
    const devices = await recipient.ref.collection('devices').get();
    for (const device of devices.docs) {
      const token = device.get('token');
      if (typeof token !== 'string') continue;
      // Data-only: no sensitive content and no OS notification after logout.
      try {
        await getMessaging().send({ token, data: {
          kind, recipientUid: recipient.id,
          eventId: createHash('sha256').update(event.id).digest('hex')
        }, android: { priority: 'high', ttl: 3600000 },
        apns: { headers: { 'apns-push-type': 'background', 'apns-priority': '5' }, payload: { aps: { contentAvailable: true } } } });
      } catch (error) {
        if (['messaging/registration-token-not-registered', 'messaging/invalid-registration-token'].includes(error.code)) await device.ref.delete();
        else throw error;
      }
    }
  }
}
for (const kind of ['leaveRequests', 'messages', 'tickets', 'companyDocuments']) {
  exports[`${kind}Notification`] = onDocumentWritten({ document: `${kind}/{id}`, region: 'europe-west3', retry: true }, event => deliver(event, kind));
}
