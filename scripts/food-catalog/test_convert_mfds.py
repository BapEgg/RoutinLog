import unittest
from convert_mfds import convert

class ConversionTest(unittest.TestCase):
    def row(self, **changes):
        return {'식품코드':'D-TEST','식품명':'테스트 식품','데이터구분명':'음식',
                '영양성분함량기준량':'80g','에너지(kcal)':'160','지방(g)':'0',
                '식이섬유(g)':'','데이터기준일자':'2026-08-28','출처명':'테스트 출처',**changes}

    def test_original_basis_zero_and_missing_are_distinct(self):
        food=convert(self.row())
        self.assertEqual('80',food['basisAmount'])
        self.assertEqual('160',food['nutrition']['kcal'])
        self.assertEqual('0',food['nutrition']['fatG'])
        self.assertIsNone(food['nutrition']['fiberG'])

    def test_volume_is_not_converted_to_mass(self):
        food=convert(self.row(**{'영양성분함량기준량':'100ml'}))
        self.assertEqual('ml',food['basisUnit'])
        self.assertEqual('100',food['basisAmount'])

    def test_trace_and_unrecognized_basis_keep_original_labels(self):
        food=convert(self.row(**{'단백질(g)':'미량','영양성분함량기준량':'1인분'}))
        self.assertIsNone(food['nutrition']['proteinG'])
        self.assertEqual('미량',food['nutrientNotes']['proteinG'])
        self.assertEqual('UNKNOWN',food['basisUnit'])
        self.assertEqual('1인분',food['basisLabel'])

    def test_invalid_negative_and_nonfinite_values_are_rejected(self):
        for value in ['-1','NaN','Infinity']:
            with self.assertRaises(ValueError): convert(self.row(**{'에너지(kcal)':value}))

if __name__=='__main__': unittest.main()
