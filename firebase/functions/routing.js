'use strict';
function route(kind, before, after) {
  if (!after || !after.employeeId) return null;
  const employeeId = after.employeeId;
  if (kind === 'messages') {
    if (before) return null;
    if (!['admin', 'employee'].includes(after.sender)) return null;
    return { employeeId, audience: after.sender === 'admin' ? 'employee' : 'admin' };
  }
  if (kind === 'companyDocuments') return { employeeId, audience: 'employee' };
  if (['leaveRequests', 'tickets'].includes(kind)) {
    if (!before) return { employeeId, audience: 'admin' };
    if (before.status !== after.status) return { employeeId, audience: 'employee' };
  }
  return null;
}
module.exports = { route };
