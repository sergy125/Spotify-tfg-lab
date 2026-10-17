import json
import unittest
from unittest.mock import Mock, patch
from main import APIError, Database, FEATURES, ReccoBeats, select_track, search_candidates


class DatabaseTests(unittest.TestCase):
    def setUp(self):
        self.db = Database(':memory:')
        self.track = {'id': 'abc', 'trackTitle': "It's a song", 'artists': [{'name': 'José'}], 'isrc': 'TEST'}

    def tearDown(self):
        self.db.close()

    def test_save_all_features_and_update_without_duplicates(self):
        audio = {field: 0 for field in FEATURES}
        self.db.save(self.track, audio)
        row = self.db.get('abc')
        self.assertEqual(row['artistas'], 'José')
        self.assertEqual(row['nombre'], "It's a song")
        self.assertTrue(all(row[field] == 0 for field in FEATURES))
        self.assertEqual(json.loads(row['metadata_json']), self.track)
        self.db.save(self.track, dict(audio, tempo=120))
        self.assertEqual(self.db.get('abc')['tempo'], 120)
        self.assertEqual(self.db.conn.execute('SELECT COUNT(*) FROM canciones').fetchone()[0], 1)

    def test_missing_audio_stays_null(self):
        self.db.save(self.track, None)
        row = self.db.get('abc')
        self.assertEqual(row['audio_disponible'], 0)
        self.assertTrue(all(row[field] is None for field in FEATURES))


class APITests(unittest.TestCase):
    def test_title_and_singer_rank_first_even_when_full_query_is_empty(self):
        target = {'id': 'audrey', 'trackTitle': 'Sue Me',
                  'artists': [{'name': 'Audrey Hobert'}], 'popularity': 1}
        other = {'id': 'other', 'trackTitle': 'Sue Me',
                 'artists': [{'name': 'Other Singer'}], 'popularity': 99}
        api = Mock()
        api.search.side_effect = lambda query, page: {
            'content': [other, target] if query.lower() in ('sue me', 'sue') else [],
            'last': True}
        tracks, more = search_candidates(api, 'sue me audrey hobert', 0)
        self.assertEqual([track['id'] for track in tracks], ['audrey', 'other'])
        self.assertFalse(more)
        with patch('builtins.input', return_value='1'), patch('builtins.print'):
            self.assertEqual(select_track(api, 'sue me audrey hobert'), target)

    def test_404_audio_is_missing_but_server_failure_is_error(self):
        api = ReccoBeats()
        with patch.object(api, 'get', side_effect=APIError('Missing', 404)):
            self.assertIsNone(api.features('abc'))
        with patch.object(api, 'get', side_effect=APIError('Server error', 500)):
            with self.assertRaises(APIError):
                api.features('abc')

    def test_search_url_encodes_title(self):
        with patch('main.http.client.HTTPSConnection') as factory:
            connection = factory.return_value
            connection.getresponse.return_value.status = 200
            connection.getresponse.return_value.read.return_value = b'{"content": []}'
            ReccoBeats().search('Beyoncé & Hello')
            url = connection.request.call_args.args[1]
            self.assertIn('searchText=Beyonc%C3%A9+%26+Hello', url)
            connection.close.assert_called_once()

    def test_invalid_selection_and_next_page(self):
        api = Mock()
        track = {'id': 'second', 'trackTitle': 'Hello', 'artists': [{'name': 'Adele'}]}
        api.search.side_effect = [{'content': [dict(track, id='first')], 'last': False},
                                  {'content': [track], 'last': True}]
        with patch('builtins.input', side_effect=['wrong', 'n', '1']), patch('builtins.print'):
            self.assertEqual(select_track(api, 'Hello'), track)
        self.assertEqual(api.search.call_args.args, ('Hello', 1))


if __name__ == '__main__':
    unittest.main()
