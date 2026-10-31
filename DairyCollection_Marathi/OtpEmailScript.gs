// Google Apps Script: emails the registration/renewal OTP to the owner and checks it.
// Create it while logged in to the owner's Gmail account (mchudmunge@gmail.com).

var SECRET = '5a236fc1d046134e0693dcff';            // must be the same text as OTP_SECRET in Auth.kt
var OWNER_EMAIL = 'mchudmunge@gmail.com';

function doPost(e) {
  var d = JSON.parse(e.postData.contents);
  if (d.secret !== SECRET) return out({ ok: false, error: 'unauthorized' });
  var cache = CacheService.getScriptCache();

  if (d.action === 'send') {
    var otp = String(Math.floor(100000 + Math.random() * 900000));
    var id = Utilities.getUuid();
    cache.put('otp_' + id, JSON.stringify({ otp: otp, tries: 0 }), 600);   // valid 10 minutes
    MailApp.sendEmail(
      OWNER_EMAIL,
      'Dairy App OTP: ' + otp,
      'OTP: ' + otp + '\n' +
      (d.renew ? 'RENEWAL' : 'NEW REGISTRATION') + '\n' +
      'Name: ' + d.name + '\nUser code: ' + d.code + '\nMobile: ' + d.mobile +
      '\n\nValid for 10 minutes.'
    );
    return out({ ok: true, id: id });
  }

  if (d.action === 'verify') {
    var raw = cache.get('otp_' + d.id);
    if (!raw) return out({ ok: false, error: 'expired' });
    var o = JSON.parse(raw);
    if (o.tries >= 5) return out({ ok: false, error: 'locked' });
    if (o.otp === String(d.otp)) {
      cache.remove('otp_' + d.id);
      return out({ ok: true });
    }
    o.tries++;
    cache.put('otp_' + d.id, JSON.stringify(o), 600);
    return out({ ok: false, error: o.tries >= 5 ? 'locked' : 'wrong', left: 5 - o.tries });
  }

  return out({ ok: false, error: 'bad request' });
}

function out(o) {
  return ContentService.createTextOutput(JSON.stringify(o)).setMimeType(ContentService.MimeType.JSON);
}
