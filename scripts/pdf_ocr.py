"""PDF 图片型转文字 — EasyOCR 版（限功率，防过热）"""
import sys, os, io, time
from pathlib import Path

os.environ['PYTHONIOENCODING'] = 'utf-8'
import easyocr
import fitz
from PIL import Image


def pdf_to_text(pdf_path, output_path=None, dpi=250, sleep_s=3, batch_size=3):
    """dpi=250 保证识别率，sleep_s 每页间隔散热，batch_size 每 N 页多歇 10s"""
    pdf_path = Path(pdf_path)
    if not pdf_path.exists():
        print(f'[ERROR] 文件不存在: {pdf_path}')
        return

    if output_path is None:
        output_path = str(pdf_path.parent / (pdf_path.stem + '.txt'))

    print(f'初始化 EasyOCR（CPU 模式，限功率）...')
    reader = easyocr.Reader(['ch_sim', 'en'], gpu=False, verbose=False)

    doc = fitz.open(str(pdf_path))
    total = len(doc)
    results = []

    for i in range(total):
        page_num = i + 1
        page = doc[i]

        # 先试文字层
        text = page.get_text().strip()
        if len(text) > 200:
            results.append(f'=== 第{page_num}页(文字层) ===\n{text}')
            print(f'  [{page_num}/{total}] 文字层 {len(text)}字')
            time.sleep(0.5)  # 文字层也稍歇
            continue

        # OCR
        pix = page.get_pixmap(dpi=dpi)
        img = Image.open(io.BytesIO(pix.tobytes("png")))
        img_np = __import__('numpy').array(img)
        ocr_result = reader.readtext(img_np)

        if ocr_result:
            lines = []
            for _, t, conf in ocr_result:
                if conf > 0.1:
                    lines.append(t)
            text = '\n'.join(lines)
            tag = f'第{page_num}页'
            results.append(f'=== {tag} ({len(lines)}行) ===\n{text}')
            print(f'  [{page_num}/{total}] OCR {len(lines)}行')
        else:
            results.append(f'=== 第{page_num}页 ===\n[无文字]')

        # 散热间隔
        if page_num % batch_size == 0:
            print(f'  -- 批量散热 10s ({page_num}/{total}) --')
            time.sleep(10)
        else:
            time.sleep(sleep_s)

    doc.close()
    full_text = '\n\n'.join(results)

    with open(output_path, 'w', encoding='utf-8') as f:
        f.write(full_text)

    size_kb = len(full_text) / 1024
    print(f'\n完成 → {output_path}')
    print(f'大小: {size_kb:.1f}KB, 总字数: {len(full_text)}')
    return output_path


if __name__ == '__main__':
    if len(sys.argv) < 2:
        print('用法: python pdf_ocr.py <PDF路径> [输出路径]')
        sys.exit(1)
    pdf_to_text(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else None)
