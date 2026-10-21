const { test } = require('node:test');
const assert = require('node:assert/strict');
const { route } = require('../routing');
for (const kind of ['leaveRequests', 'tickets']) {
  test(`${kind}: new requests notify admins, status changes notify owner`, () => {
    assert.deepEqual(route(kind, null, { employeeId: 'alice', status: 'Offen' }), { audience: 'admin', employeeId: 'alice' });
    assert.deepEqual(route(kind, { status: 'Offen' }, { employeeId: 'alice', status: 'Genehmigt' }), { audience: 'employee', employeeId: 'alice' });
    assert.equal(route(kind, { status: 'Offen' }, { employeeId: 'alice', status: 'Offen' }), null);
  });
}
test('messages target the other side, never the sender or another employee', () => {
  assert.deepEqual(route('messages', null, { employeeId: 'alice', sender: 'employee' }), { audience: 'admin', employeeId: 'alice' });
  assert.deepEqual(route('messages', null, { employeeId: 'alice', sender: 'admin' }), { audience: 'employee', employeeId: 'alice' });
  assert.equal(route('messages', {}, { employeeId: 'alice', sender: 'admin' }), null);
  assert.equal(route('messages', null, { employeeId: 'alice', sender: 'invalid' }), null);
});
test('documents notify only assigned owner; deletions and unassigned documents do not notify', () => {
  assert.deepEqual(route('companyDocuments', null, { employeeId: 'alice' }), { audience: 'employee', employeeId: 'alice' });
  for (const kind of ['leaveRequests', 'tickets', 'messages', 'companyDocuments']) {
    assert.equal(route(kind, null, null), null);
    assert.equal(route(kind, null, { employeeId: '' }), null);
  }
});
