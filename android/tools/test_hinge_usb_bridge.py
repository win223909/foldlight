import queue
import struct
import threading
import unittest
from unittest.mock import patch
import hinge_usb_bridge as bridge

class BridgeTests(unittest.TestCase):
    def test_no_timeout_is_the_default(self):
        self.assertEqual(bridge.arguments(['--serial','test']).seconds,0)
        self.assertEqual(bridge.arguments(['--serial','test','--continuous']).seconds,0)
        self.assertEqual(bridge.arguments(['--serial','test','--seconds','30']).seconds,30)

    def test_parser_preserves_original_measurement_timestamp(self):
        self.assertEqual(bridge.parse('handle_sns_client_event:107, [0]folding_angle ts=123456789 ns value [ 97/0]'),(123456789,97))
        self.assertIsNone(bridge.parse('arbitrary text [0]folding_angle ts=123 ns value [97/0]'))
        self.assertIsNone(bridge.parse('handle_sns_client_event:107, [0]folding_angle ts=123 ns value [999/0]'))

    def test_angle_delivery_continues_while_display_control_is_blocked(self):
        stopping,entered,release=threading.Event(),threading.Event(),threading.Event()
        packets=[]
        class SlowController(bridge.DualController):
            def _update(self):entered.set();release.wait(2)
            def close(self):release.set();super().close()
        class Samples:
            def __init__(self):self.values=iter((1000+i,float(i)) for i in range(10))
            def get(self,timeout):
                if not entered.wait(2):raise AssertionError('controller not started')
                return next(self.values)
        class Stream:
            def __init__(self,adb):self.samples=Samples();self.logs=self
            def poll(self):return None
            def close(self):pass
        class Connection:
            def sendall(self,packet):
                if release.is_set():raise AssertionError('display operation blocked the angle sender')
                packets.append(struct.unpack('>qf',packet))
                if len(packets)==10:stopping.set()
            def recv(self,count):return b'\x01'
        with patch.object(bridge,'AngleStream',Stream),patch.object(bridge,'DualController',SlowController):
            bridge.run_session(Connection(),[],True,float('inf'),stopping)
        self.assertEqual([p[0] for p in packets],list(range(1000,1010)))

    def test_closed_app_does_not_start_a_sensor_reader(self):
        stopping=threading.Event()
        class Connection:
            def __enter__(self):return self
            def __exit__(self,*args):pass
            def setsockopt(self,*args):pass
            def sendall(self,*args):pass
            def recv(self,count):stopping.set();return b''
        def output(command,**kwargs):
            if command[-1]=='get-state':return 'device\n'
            if 'run-as' in command:return '10381\n'
            if 'forward' in command:return '12345\n'
            raise AssertionError(command)
        options=bridge.arguments(['--serial','test','--watch-app'])
        with patch.object(bridge.socket,'create_connection',return_value=Connection()),patch.object(bridge.subprocess,'check_output',side_effect=output),patch.object(bridge.subprocess,'run'),patch.object(bridge,'AngleStream') as stream:
            bridge.watch(options,stopping)
            stream.assert_not_called()

if __name__=='__main__':unittest.main()
