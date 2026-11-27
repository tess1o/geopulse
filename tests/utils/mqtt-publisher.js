import net from 'net';

/**
 * Publishes one message to the e2e Mosquitto broker with a minimal MQTT 3.1.1 client (CONNECT, PUBLISH at QoS 1,
 * wait for PUBACK, DISCONNECT), so the suite needs no MQTT library. MQTT_BROKER_URL points at the broker:
 * mqtt://geopulse-mosquitto-e2e:1883 inside the compose network, mqtt://localhost:1884 (the default) from the host.
 */
export async function publishMqtt(topic, payload, {timeoutMs = 10000} = {}) {
  const url = new URL(process.env.MQTT_BROKER_URL || 'mqtt://localhost:1884');
  const socket = net.connect({host: url.hostname, port: Number(url.port || 1883)});
  const packetId = 1;

  return new Promise((resolve, reject) => {
    let buffer = Buffer.alloc(0);
    const timer = setTimeout(() => fail(new Error(`MQTT publish to ${url.host} timed out`)), timeoutMs);

    function finish() {
      clearTimeout(timer);
      socket.end(Buffer.from([0xe0, 0x00])); // DISCONNECT
      resolve();
    }

    function fail(error) {
      clearTimeout(timer);
      socket.destroy();
      reject(error);
    }

    socket.on('error', fail);
    socket.on('connect', () => socket.write(connectPacket(`e2e-${process.pid}-${Date.now()}`)));
    socket.on('data', (chunk) => {
      buffer = Buffer.concat([buffer, chunk]);
      while (buffer.length >= 2) {
        const type = buffer[0] >> 4;
        const length = buffer[1]; // CONNACK and PUBACK have a one-byte remaining length
        if (buffer.length < 2 + length) return;
        const body = buffer.subarray(2, 2 + length);
        buffer = buffer.subarray(2 + length);
        if (type === 2) { // CONNACK
          if (body[1] !== 0) return fail(new Error(`MQTT broker refused the connection (code ${body[1]})`));
          socket.write(publishPacket(topic, payload, packetId));
        } else if (type === 4 && body.readUInt16BE(0) === packetId) { // PUBACK
          finish();
        }
      }
    });
  });
}

function connectPacket(clientId) {
  const variableHeader = Buffer.concat([
    utf8('MQTT'),
    Buffer.from([0x04, 0x02, 0x00, 0x1e]), // protocol level 4 (3.1.1), clean session, keep-alive 30 s
  ]);
  return packet(0x10, Buffer.concat([variableHeader, utf8(clientId)]));
}

function publishPacket(topic, payload, packetId) {
  const id = Buffer.alloc(2);
  id.writeUInt16BE(packetId);
  return packet(0x32, Buffer.concat([utf8(topic), id, Buffer.from(payload, 'utf8')])); // QoS 1
}

function packet(header, body) {
  return Buffer.concat([Buffer.from([header]), remainingLength(body.length), body]);
}

function utf8(value) {
  const bytes = Buffer.from(value, 'utf8');
  const length = Buffer.alloc(2);
  length.writeUInt16BE(bytes.length);
  return Buffer.concat([length, bytes]);
}

function remainingLength(length) {
  const bytes = [];
  do {
    let byte = length % 128;
    length = Math.floor(length / 128);
    if (length > 0) byte |= 0x80;
    bytes.push(byte);
  } while (length > 0);
  return Buffer.from(bytes);
}
