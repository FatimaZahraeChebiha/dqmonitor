import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { provideRouter } from '@angular/router';
import { ReportsCompare, buildCompareRows, classifyCheck } from './reports-compare';
import { ReportCheckResult } from '../../../core/models/report.model';
import { getTranslocoTestingModule } from '../../../../testing/transloco-testing';

function check(overrides: Partial<ReportCheckResult>): ReportCheckResult {
  return { check_name: 'check', success: true, unexpected_count: 0, unexpected_pct: 0, ...overrides };
}

describe('classifyCheck', () => {
  it('flags a check only present in the newer report as new', () => {
    expect(classifyCheck(null, check({}))).toBe('NEW');
  });

  it('flags a check only present in the older report as removed', () => {
    expect(classifyCheck(check({}), null)).toBe('REMOVED');
  });

  it('flags a pass-to-fail transition as a regression', () => {
    expect(classifyCheck(check({ success: true }), check({ success: false }))).toBe('REGRESSION');
  });

  it('flags a fail-to-pass transition as fixed', () => {
    expect(classifyCheck(check({ success: false }), check({ success: true }))).toBe('FIXED');
  });

  it('flags two passes as still compliant', () => {
    expect(classifyCheck(check({ success: true }), check({ success: true }))).toBe('STILL_COMPLIANT');
  });

  it('flags a failing check with a lower anomaly count as improved', () => {
    const status = classifyCheck(
      check({ success: false, unexpected_count: 10 }),
      check({ success: false, unexpected_count: 3 }),
    );
    expect(status).toBe('IMPROVED');
  });

  it('flags a failing check with a higher anomaly count as degraded', () => {
    const status = classifyCheck(
      check({ success: false, unexpected_count: 3 }),
      check({ success: false, unexpected_count: 10 }),
    );
    expect(status).toBe('DEGRADED');
  });

  it('flags a failing check with the same anomaly count as unchanged', () => {
    const status = classifyCheck(
      check({ success: false, unexpected_count: 5 }),
      check({ success: false, unexpected_count: 5 }),
    );
    expect(status).toBe('UNCHANGED');
  });
});

describe('buildCompareRows', () => {
  it('unions checks from both reports by check_name, matched regardless of array order', () => {
    const before = [check({ check_name: 'a', success: true }), check({ check_name: 'b', success: false })];
    const after = [check({ check_name: 'b', success: true }), check({ check_name: 'c', success: false })];

    const rows = buildCompareRows(before, after);

    expect(rows).toHaveLength(3);
    expect(rows.find((r) => r.checkName === 'a')?.status).toBe('REMOVED');
    expect(rows.find((r) => r.checkName === 'b')?.status).toBe('FIXED');
    expect(rows.find((r) => r.checkName === 'c')?.status).toBe('NEW');
  });
});

describe('ReportsCompare', () => {
  let fixture: ComponentFixture<ReportsCompare>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ReportsCompare, getTranslocoTestingModule()],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap({}) } },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ReportsCompare);
    await fixture.whenStable();
  });

  it('flags a load error when the a/b query params are missing', () => {
    expect(fixture.componentInstance.loadError()).toBe(true);
  });
});
