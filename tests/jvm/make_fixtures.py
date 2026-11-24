import datetime, docx, openpyxl, zipfile
from odf.opendocument import OpenDocumentSpreadsheet, OpenDocumentText
from odf.table import Table, TableRow, TableCell
from odf.text import P, H, List, ListItem
d = docx.Document(); d.add_heading('Timetable Notes', 0); d.add_heading('Semester One', 1); d.add_paragraph('Classes start on Monday.')
d.add_paragraph('Bring your ID card', style='List Bullet'); d.add_paragraph('Pay fees early', style='List Bullet')
t = d.add_table(rows=2, cols=3)
for i, v in enumerate(['Day', 'Time', 'Venue']): t.cell(0, i).text = v
for i, v in enumerate(['Monday', '08:00', 'LH1']): t.cell(1, i).text = v
d.add_paragraph('Footer line'); d.save('t.docx')
wb = openpyxl.Workbook(); ws = wb.active; ws.title = 'Courses'
ws.append(['Code', 'Title', 'Credits', 'Start']); ws.append(['CS210', 'Databases', 3, datetime.date(2026, 2, 2)])
ws.append(['CS220', None, 2.5, datetime.datetime(2026, 2, 3, 9, 30)]); ws['E4'] = True; ws['A5'] = '=1+1'; ws['B6'] = 'Ünïcode ✓'
w2 = wb.create_sheet('Rooms'); w2.append(['Room', 'Seats']); w2.append(['LH1', 120])
w3 = wb.create_sheet('Secret'); w3.sheet_state = 'hidden'; w3.append(['x']); wb.save('t.xlsx')
s = OpenDocumentSpreadsheet(); tb = Table(name='Rooms')
for row in [['Room', 'Seats'], ['LH1', '120']]:
    tr = TableRow()
    for v in row:
        c = TableCell(valuetype='string'); c.addElement(P(text=v)); tr.addElement(c)
    tb.addElement(tr)
tr = TableRow(numberrowsrepeated=100000); tr.addElement(TableCell(numbercolumnsrepeated=1024)); tb.addElement(tr)
s.spreadsheet.addElement(tb); s.save('t.ods')
o = OpenDocumentText(); o.text.addElement(H(outlinelevel=1, text='Intro')); o.text.addElement(P(text='Hello world'))
l = List(); li = ListItem(); li.addElement(P(text='first item')); l.addElement(li); o.text.addElement(l); o.save('t.odt')
open('t.csv', 'w').write('Code,Title,Notes\nCS210,"Databases, advanced","say ""hi"""\nCS220,Networks,\n')
open('semi.csv', 'w').write('a;b;c\n1;2;3\n'); open('t.tsv', 'w').write('a\tb\n1\t2\n')
z = zipfile.ZipFile('t.zip', 'w'); z.writestr('a.txt', 'hello'); z.writestr('dir/b.txt', 'x' * 10); z.close()
