export async function sendPushNotification(
  fcmServerKey: string,
  tokens: string[],
  payload: Record<string, string>,
): Promise<void> {
  if (!fcmServerKey || tokens.length === 0) return

  try {
    const response = await fetch('https://fcm.googleapis.com/fcm/send', {
      method: 'POST',
      headers: {
        'Authorization': `key=${fcmServerKey}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ registration_ids: tokens, data: payload }),
    })

    if (!response.ok) {
      console.error('FCM send failed', response.status, await response.text())
    }
  } catch (e) {
    console.error('FCM send error', e instanceof Error ? e.message : e)
  }
}

